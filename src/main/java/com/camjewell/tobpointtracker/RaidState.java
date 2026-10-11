package com.camjewell.tobpointtracker;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Everything observed about the current raid. Player names are keyed by their standardized form (see
 * {@link net.runelite.client.util.Text#standardize}) and the display form is kept for rendering.
 */
@Getter
public class RaidState
{
	public static final int MAX_TEAM_SIZE = 5;

	// standardized name -> display name, in party slot order
	private final Map<String, String> team = new LinkedHashMap<>();
	private final EnumSet<Segment> completed = EnumSet.noneOf(Segment.class);
	private final Map<String, Integer> deaths = new HashMap<>();
	// "room:name" keys, since a dead player spectates for the rest of the room and can't die twice in it
	private final Set<String> deathKeys = new HashSet<>();
	// Players currently dead. Death sources can report the same death several ticks apart (the party orbs lag behind
	// ActorDeath), sometimes after the room has ended, so a player can't die again until they've been seen alive.
	private final Set<String> deadPlayers = new HashSet<>();
	// Damage per segment that we know is attributed to a player: ours from our own hitsplats, others from party updates
	private final Map<String, int[]> damage = new HashMap<>();
	// All boss damage hitsplats we saw, regardless of who dealt them
	private final int[] totalDamage = new int[Segment.count()];

	private final String localName;

	@Setter
	private RaidMode mode = RaidMode.NORMAL;
	@Setter
	private boolean modeKnown;

	public RaidState(String localName)
	{
		this.localName = localName;
		damage.put(localName, new int[Segment.count()]);
	}

	public void addTeamMember(String key, String displayName)
	{
		if (team.size() < MAX_TEAM_SIZE || team.containsKey(key))
		{
			team.putIfAbsent(key, displayName);
		}
	}

	public int getTeamSize()
	{
		return Math.max(1, team.size());
	}

	/**
	 * The room currently being fought, or {@link Segment#ROOM_COUNT} once Verzik is dead.
	 */
	public int getCurrentRoom()
	{
		for (Segment s : Segment.values())
		{
			if (!completed.contains(s))
			{
				return s.getRoom();
			}
		}
		return Segment.ROOM_COUNT;
	}

	public Segment getCurrentSegment()
	{
		for (Segment s : Segment.values())
		{
			if (!completed.contains(s))
			{
				return s;
			}
		}
		return null;
	}

	public int getRoomsCompleted()
	{
		int rooms = 0;
		for (Segment s : completed)
		{
			if (s.endsRoom())
			{
				rooms++;
			}
		}
		return rooms;
	}

	public boolean isComplete()
	{
		return completed.contains(Segment.VERZIK_P3);
	}

	/**
	 * Marks a segment and every segment before it as complete, since the raid is linear and an event for an earlier
	 * segment may have been missed (e.g. the boss died while we were loading).
	 *
	 * @return whether anything changed
	 */
	public boolean completeThrough(Segment segment)
	{
		boolean changed = false;
		for (Segment s : Segment.values())
		{
			if (s.ordinal() > segment.ordinal())
			{
				break;
			}
			changed |= completed.add(s);
		}
		return changed;
	}

	/**
	 * @return whether this was a new death (the player wasn't already dead, and hasn't died in the current room)
	 */
	public boolean recordDeath(String key)
	{
		if (isComplete() || !team.containsKey(key) || deadPlayers.contains(key)
			|| !deathKeys.add(getCurrentRoom() + ":" + key))
		{
			return false;
		}
		deadPlayers.add(key);
		deaths.merge(key, 1, Integer::sum);
		return true;
	}

	/**
	 * Marks a dead player as alive again, once they've respawned for the next room.
	 */
	public void markAlive(String key)
	{
		deadPlayers.remove(key);
	}

	public int getDeaths(String key)
	{
		return deaths.getOrDefault(key, 0);
	}

	public int getTotalDeaths()
	{
		int total = 0;
		for (String key : team.keySet())
		{
			total += getDeaths(key);
		}
		return total;
	}

	public void recordHit(Segment segment, int amount, boolean mine)
	{
		totalDamage[segment.ordinal()] += amount;
		if (mine)
		{
			damage.get(localName)[segment.ordinal()] += amount;
		}
	}

	public int[] getLocalDamage()
	{
		return damage.get(localName);
	}

	public void setPartyDamage(String key, int[] segmentDamage)
	{
		if (!key.equals(localName) && segmentDamage != null && segmentDamage.length == Segment.count())
		{
			damage.put(key, segmentDamage.clone());
		}
	}

	public void clearPartyDamage()
	{
		damage.keySet().removeIf(key -> !key.equals(localName));
	}

	public Map<String, int[]> getDamage()
	{
		return Collections.unmodifiableMap(damage);
	}
}
