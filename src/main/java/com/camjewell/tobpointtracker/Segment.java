package com.camjewell.tobpointtracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A scoring segment of the raid. Each room awards {@link #POINTS_PER_ROOM} to every player, and each segment awards its
 * MVP points to whoever dealt the most damage to the boss during it. Verzik is one room split into three MVP segments.
 * MVP points total 14 per raid.
 */
@Getter
@RequiredArgsConstructor
public enum Segment
{
	MAIDEN("Maiden", 0, 2),
	BLOAT("Bloat", 1, 2),
	NYLOCAS("Nylocas", 2, 1),
	SOTETSEG("Sotetseg", 3, 1),
	XARPUS("Xarpus", 4, 2),
	VERZIK_P1("Verzik P1", 5, 2),
	VERZIK_P2("Verzik P2", 5, 2),
	VERZIK_P3("Verzik P3", 5, 2);

	public static final int ROOM_COUNT = 6;
	public static final int POINTS_PER_ROOM = 3;
	public static final int DEATH_PENALTY = 4;
	public static final int TOTAL_MVP_POINTS = 14;
	public static final int MAX_PLAYER_POINTS = ROOM_COUNT * POINTS_PER_ROOM + TOTAL_MVP_POINTS;

	private static final Segment[] VALUES = values();

	private final String displayName;
	private final int room;
	private final int mvpPoints;

	/**
	 * Whether completing this segment completes its room (and so awards room points).
	 */
	public boolean endsRoom()
	{
		return ordinal() == VALUES.length - 1 || VALUES[ordinal() + 1].room != room;
	}

	public static int count()
	{
		return VALUES.length;
	}
}
