package com.camjewell.tobpointtracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Applies the Theatre of Blood points formula (as modelled by the OSRS Wiki loot calculator):
 * <ul>
 *     <li>Every player earns 3 points per room completed (18 max).</li>
 *     <li>14 MVP points are split per segment to whoever dealt the most damage to the boss.</li>
 *     <li>Every death costs the dying player 4 points. A player can't go below 0.</li>
 *     <li>Team max is {@code 18 * teamSize + 14}, so a solo raid maxes at 32 and a 5-man at 104.</li>
 *     <li>Team unique chance = base rate (1/9.1 normal, 1/7.7 hard, none in entry) * team points / team max.</li>
 *     <li>If a unique is rolled, each player's chance of receiving it is their points / team points.</li>
 * </ul>
 */
public final class PointCalculator
{
	private PointCalculator()
	{
	}

	@Value
	public static class PlayerScore
	{
		String name;
		boolean local;
		int roomPoints;
		int deaths;
		/**
		 * MVP points the player has provably earned.
		 */
		int mvpWon;
		/**
		 * MVP points the player might have earned, which can't be proven either way without every teammate's damage.
		 */
		int mvpUnknown;
		/**
		 * Whether we have this player's damage, which is only true for us and RuneLite party members running the plugin.
		 */
		boolean damageKnown;
		/**
		 * The MVP outcome of each completed segment, indexed by {@link Segment} ordinal (null if not yet completed).
		 */
		MvpOutcome[] outcomes;
		int minPoints;
		int maxPoints;
		/**
		 * The player's chance of personally receiving a unique (team chance times their share of the team's points),
		 * assuming they lost / won every MVP that can't be proven.
		 */
		double minUniqueChance;
		double maxUniqueChance;

		public boolean isExact()
		{
			return minPoints == maxPoints;
		}
	}

	@Value
	public static class RaidScore
	{
		RaidMode mode;
		int teamSize;
		int teamPoints;
		int maxPoints;
		/**
		 * Team points at the end of the raid if every remaining room is cleared without another death.
		 */
		int projectedTeamPoints;
		int totalDeaths;
		/**
		 * Team chance of a unique, based on {@link #projectedTeamPoints} or {@link #teamPoints} depending on the
		 * {@link ChanceBasis}.
		 */
		double teamUniqueChance;
		boolean complete;
		List<PlayerScore> players;
	}

	public static int maxTeamPoints(int teamSize)
	{
		return Segment.ROOM_COUNT * Segment.POINTS_PER_ROOM * teamSize + Segment.TOTAL_MVP_POINTS;
	}

	public static double teamUniqueChance(RaidMode mode, int teamPoints, int teamSize)
	{
		return mode.getBaseUniqueChance() * Math.max(0, teamPoints) / maxTeamPoints(teamSize);
	}

	public static RaidScore calculate(RaidState raid)
	{
		return calculate(raid, ChanceBasis.PROJECTED);
	}

	public static RaidScore calculate(RaidState raid, ChanceBasis basis)
	{
		final int teamSize = raid.getTeamSize();
		final Map<String, String> team = raid.getTeam();
		final List<String> keys = new ArrayList<>(team.keySet());

		MvpOutcome[][] outcomes = new MvpOutcome[keys.size()][Segment.count()];
		int awardedMvp = 0;
		for (Segment segment : raid.getCompleted())
		{
			awardedMvp += segment.getMvpPoints();
			MvpOutcome[] segmentOutcomes = mvpOutcomes(raid, keys, segment);
			for (int i = 0; i < keys.size(); i++)
			{
				outcomes[i][segment.ordinal()] = segmentOutcomes[i];
			}
		}

		final int totalDeaths = raid.getTotalDeaths();
		final int roomPoints = raid.getRoomsCompleted() * Segment.POINTS_PER_ROOM;
		final int teamPoints = Math.max(0, roomPoints * teamSize + awardedMvp - totalDeaths * Segment.DEATH_PENALTY);
		final int maxPoints = maxTeamPoints(teamSize);
		final int projected = Math.max(0, maxPoints - totalDeaths * Segment.DEATH_PENALTY);
		final double teamChance = teamUniqueChance(raid.getMode(),
			basis == ChanceBasis.CURRENT ? teamPoints : projected, teamSize);

		List<PlayerScore> players = new ArrayList<>(keys.size());
		for (int i = 0; i < keys.size(); i++)
		{
			String key = keys.get(i);
			int won = 0;
			int unknown = 0;
			for (Segment segment : raid.getCompleted())
			{
				MvpOutcome outcome = outcomes[i][segment.ordinal()];
				if (outcome == MvpOutcome.WON)
				{
					won += segment.getMvpPoints();
				}
				else if (outcome == MvpOutcome.UNKNOWN)
				{
					unknown += segment.getMvpPoints();
				}
			}

			int deaths = raid.getDeaths(key);
			int base = roomPoints - deaths * Segment.DEATH_PENALTY;
			int min = Math.max(0, base + won);
			int max = Math.max(0, base + won + unknown);
			players.add(new PlayerScore(
				team.get(key),
				key.equals(raid.getLocalName()),
				roomPoints,
				deaths,
				won,
				unknown,
				raid.getDamage().containsKey(key),
				outcomes[i],
				min,
				max,
				teamChance * share(min, teamPoints, keys.size()),
				teamChance * share(max, teamPoints, keys.size())));
		}

		return new RaidScore(raid.getMode(), teamSize, teamPoints, maxPoints, projected, totalDeaths, teamChance,
			raid.isComplete(), players);
	}

	/**
	 * Fraction of the team's points a player holds, i.e. the chance a rolled unique goes to them. Before anyone has
	 * earned points, everyone has an even share.
	 */
	private static double share(int points, int teamPoints, int players)
	{
		return teamPoints > 0 ? Math.min(1, (double) points / teamPoints) : 1.0 / Math.max(1, players);
	}

	/**
	 * Works out what can be proven about each player's MVP for a segment, without guessing how damage we can't
	 * attribute was split between teammates who aren't sharing it.
	 * <p>
	 * For a player whose damage {@code d} we know, with {@code k} teammates not sharing data who between them dealt
	 * {@code u} damage that we saw but can't attribute:
	 * <ul>
	 *     <li>WON if {@code d} beats every other known player, and beats {@code u} (since any one of them dealt at most
	 *     {@code u}).</li>
	 *     <li>LOST if another known player dealt more, or {@code u > k * d} (so at least one of them must have dealt
	 *     more than {@code d}).</li>
	 *     <li>Otherwise UNKNOWN. Exact ties are also UNKNOWN, as we don't know how the game breaks them.</li>
	 * </ul>
	 * A player without data is LOST if someone else provably WON, and otherwise UNKNOWN.
	 */
	static MvpOutcome[] mvpOutcomes(RaidState raid, List<String> keys, Segment segment)
	{
		final int n = keys.size();
		int[] dmg = new int[n];
		boolean[] known = new boolean[n];
		int knownTotal = 0;
		int unknownPlayers = 0;
		for (int i = 0; i < n; i++)
		{
			int[] d = raid.getDamage().get(keys.get(i));
			if (d == null)
			{
				unknownPlayers++;
				continue;
			}
			known[i] = true;
			dmg[i] = d[segment.ordinal()];
			knownTotal += dmg[i];
		}
		final int unattributed = Math.max(0, raid.getTotalDamage()[segment.ordinal()] - knownTotal);

		MvpOutcome[] result = new MvpOutcome[n];
		boolean anyoneWon = false;
		for (int i = 0; i < n; i++)
		{
			if (!known[i])
			{
				continue;
			}

			boolean beaten = false;
			boolean tied = false;
			for (int j = 0; j < n; j++)
			{
				if (j != i && known[j])
				{
					beaten |= dmg[j] > dmg[i];
					tied |= dmg[j] == dmg[i];
				}
			}

			if (beaten || (unknownPlayers > 0 && unattributed > (long) unknownPlayers * dmg[i]))
			{
				result[i] = MvpOutcome.LOST;
			}
			else if (!tied && dmg[i] > 0 && (unknownPlayers == 0 || dmg[i] > unattributed))
			{
				result[i] = MvpOutcome.WON;
				anyoneWon = true;
			}
			else
			{
				result[i] = MvpOutcome.UNKNOWN;
			}
		}

		for (int i = 0; i < n; i++)
		{
			if (!known[i])
			{
				result[i] = anyoneWon ? MvpOutcome.LOST : MvpOutcome.UNKNOWN;
			}
		}
		return result;
	}
}
