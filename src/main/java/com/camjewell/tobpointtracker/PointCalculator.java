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
		int mvpPoints;
		int deaths;
		int points;
		/**
		 * Whether we have this player's damage, which is only true for us and RuneLite party members running the plugin.
		 */
		boolean damageKnown;
		/**
		 * Whether these points rely on an MVP estimate, rather than being exact.
		 */
		boolean estimated;
		/**
		 * Fraction of the team's points this player holds, i.e. the chance a rolled unique goes to them.
		 */
		double share;
		/**
		 * The player's chance of personally receiving a unique, if the raid finishes with no further deaths.
		 */
		double uniqueChance;
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
		/**
		 * MVP points from completed segments whose winner is estimated rather than certain.
		 */
		int estimatedMvpPoints;
		int totalDeaths;
		/**
		 * Team chance of a unique, based on {@link #projectedTeamPoints}.
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
		final int teamSize = raid.getTeamSize();
		final int roomsCompleted = raid.getRoomsCompleted();
		final Map<String, String> team = raid.getTeam();

		int[] mvpByPlayer = new int[team.size()];
		List<String> keys = new ArrayList<>(team.keySet());
		int awardedMvp = 0;
		int estimatedMvp = 0;
		for (Segment segment : raid.getCompleted())
		{
			awardedMvp += segment.getMvpPoints();
			MvpResult mvp = findMvp(raid, keys, segment);
			if (mvp.winner >= 0)
			{
				mvpByPlayer[mvp.winner] += segment.getMvpPoints();
			}
			if (!mvp.certain)
			{
				estimatedMvp += segment.getMvpPoints();
			}
		}

		final int totalDeaths = raid.getTotalDeaths();
		final int roomPoints = roomsCompleted * Segment.POINTS_PER_ROOM;
		final int teamPoints = Math.max(0, roomPoints * teamSize + awardedMvp - totalDeaths * Segment.DEATH_PENALTY);
		final int maxPoints = maxTeamPoints(teamSize);
		final int projected = Math.max(0, maxPoints - totalDeaths * Segment.DEATH_PENALTY);
		final double teamChance = teamUniqueChance(raid.getMode(), projected, teamSize);

		List<PlayerScore> players = new ArrayList<>(keys.size());
		for (int i = 0; i < keys.size(); i++)
		{
			String key = keys.get(i);
			int deaths = raid.getDeaths(key);
			int points = Math.max(0, roomPoints + mvpByPlayer[i] - deaths * Segment.DEATH_PENALTY);
			double share = teamPoints > 0 ? (double) points / teamPoints : 1.0 / keys.size();
			boolean damageKnown = raid.getDamage().containsKey(key);
			players.add(new PlayerScore(
				team.get(key),
				key.equals(raid.getLocalName()),
				roomPoints,
				mvpByPlayer[i],
				deaths,
				points,
				damageKnown,
				!damageKnown || estimatedMvp > 0,
				share,
				teamChance * share));
		}

		return new RaidScore(raid.getMode(), teamSize, teamPoints, maxPoints, projected, estimatedMvp, totalDeaths,
			teamChance, raid.isComplete(), players);
	}

	static final class MvpResult
	{
		/**
		 * Index into the team of the (possibly estimated) MVP, or -1 if it's thought to be a teammate without party
		 * data, or can't be guessed at all.
		 */
		final int winner;
		final boolean certain;

		MvpResult(int winner, boolean certain)
		{
			this.winner = winner;
			this.certain = certain;
		}
	}

	/**
	 * Finds the player who dealt the most damage in a segment.
	 * <p>
	 * It's certain when everyone shares damage, or when the leader among players we have damage for also out-damages
	 * everything we saw but can't attribute (since all of it might belong to a single teammate without party data).
	 * <p>
	 * Otherwise it's estimated by assuming the unattributed damage was split evenly between the teammates without data:
	 * the known leader wins if they beat that average, and if not the MVP goes to one of those teammates.
	 */
	static MvpResult findMvp(RaidState raid, List<String> keys, Segment segment)
	{
		int best = -1;
		int bestDamage = -1;
		boolean tied = false;
		int knownTotal = 0;
		int unknownPlayers = 0;

		for (int i = 0; i < keys.size(); i++)
		{
			int[] dmg = raid.getDamage().get(keys.get(i));
			if (dmg == null)
			{
				unknownPlayers++;
				continue;
			}

			int d = dmg[segment.ordinal()];
			knownTotal += d;
			if (d > bestDamage)
			{
				best = i;
				bestDamage = d;
				tied = false;
			}
			else if (d == bestDamage)
			{
				tied = true;
			}
		}

		if (best < 0 || tied || bestDamage <= 0)
		{
			return new MvpResult(-1, false);
		}

		if (unknownPlayers == 0)
		{
			return new MvpResult(best, true);
		}

		int unattributed = Math.max(0, raid.getTotalDamage()[segment.ordinal()] - knownTotal);
		if (bestDamage > unattributed)
		{
			return new MvpResult(best, true);
		}

		double averageUnknown = (double) unattributed / unknownPlayers;
		return new MvpResult(bestDamage > averageUnknown ? best : -1, false);
	}
}
