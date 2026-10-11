package com.camjewell.tobpointtracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PointCalculatorTest
{
	private static final double EPSILON = 1e-9;

	private static RaidState raid(String... names)
	{
		RaidState raid = new RaidState(names[0]);
		for (String name : names)
		{
			raid.addTeamMember(name, name);
		}
		return raid;
	}

	private static PointCalculator.PlayerScore player(PointCalculator.RaidScore score, String name)
	{
		return score.getPlayers().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow(AssertionError::new);
	}

	@Test
	public void maxPointsScaleWithTeamSize()
	{
		assertEquals(32, PointCalculator.maxTeamPoints(1));
		assertEquals(50, PointCalculator.maxTeamPoints(2));
		assertEquals(68, PointCalculator.maxTeamPoints(3));
		assertEquals(86, PointCalculator.maxTeamPoints(4));
		assertEquals(104, PointCalculator.maxTeamPoints(5));
		assertEquals(32, Segment.MAX_PLAYER_POINTS);
	}

	@Test
	public void mvpPointsTotalFourteenAcrossSixRooms()
	{
		int mvp = 0;
		int rooms = 0;
		for (Segment s : Segment.values())
		{
			mvp += s.getMvpPoints();
			rooms += s.endsRoom() ? 1 : 0;
		}
		assertEquals(14, mvp);
		assertEquals(6, rooms);
	}

	@Test
	public void soloDeathlessRaidIsPerfect()
	{
		RaidState raid = raid("me");
		raid.recordHit(Segment.MAIDEN, 100, true);
		for (Segment s : Segment.values())
		{
			raid.recordHit(s, 10, true);
		}
		raid.completeThrough(Segment.VERZIK_P3);

		PointCalculator.RaidScore score = PointCalculator.calculate(raid);
		assertTrue(score.isComplete());
		assertEquals(32, score.getTeamPoints());
		assertEquals(32, player(score, "me").getPoints());
		assertEquals(1 / 9.1, score.getTeamUniqueChance(), EPSILON);
		assertEquals(1 / 9.1, player(score, "me").getUniqueChance(), EPSILON);
	}

	@Test
	public void deathsCostFourPointsAndReduceTeamChance()
	{
		RaidState raid = raid("me", "a", "b", "c");
		raid.setMode(RaidMode.HARD);
		raid.recordDeath("a");
		raid.recordDeath("a"); // same room, can't die twice
		raid.completeThrough(Segment.MAIDEN);
		raid.recordDeath("a");

		PointCalculator.RaidScore score = PointCalculator.calculate(raid);
		assertEquals(2, score.getTotalDeaths());
		assertEquals(86 - 8, score.getProjectedTeamPoints());
		assertEquals((1 / 7.7) * 78 / 86, score.getTeamUniqueChance(), EPSILON);
		// 3 room points - 8, floored at 0
		assertEquals(0, player(score, "a").getPoints());
	}

	@Test
	public void entryModeCannotRollUniques()
	{
		RaidState raid = raid("me", "a");
		raid.setMode(RaidMode.ENTRY);
		raid.completeThrough(Segment.VERZIK_P3);

		PointCalculator.RaidScore score = PointCalculator.calculate(raid);
		assertEquals(50, score.getTeamPoints());
		assertEquals(0, score.getTeamUniqueChance(), EPSILON);
		assertFalse(RaidMode.ENTRY.canRollUniques());
	}

	@Test
	public void mvpGoesToTopDamagerWhenEveryoneSharesData()
	{
		RaidState raid = raid("me", "a");
		raid.recordHit(Segment.MAIDEN, 300, true);
		raid.recordHit(Segment.MAIDEN, 500, false);
		raid.setPartyDamage("a", damage(Segment.MAIDEN, 500));
		raid.completeThrough(Segment.MAIDEN);

		PointCalculator.RaidScore score = PointCalculator.calculate(raid);
		assertEquals(0, score.getEstimatedMvpPoints());
		assertEquals(3, player(score, "me").getPoints());
		assertEquals(5, player(score, "a").getPoints());
		assertEquals(8, score.getTeamPoints());
	}

	@Test
	public void mvpIsEstimatedForUsWhenWeBeatTheAverageUnknownTeammate()
	{
		RaidState raid = raid("me", "a", "b");
		raid.recordHit(Segment.MAIDEN, 400, true);
		raid.recordHit(Segment.MAIDEN, 600, false); // a and b combined, unknown split, average 300
		raid.completeThrough(Segment.MAIDEN);

		PointCalculator.RaidScore score = PointCalculator.calculate(raid);
		assertEquals(2, score.getEstimatedMvpPoints());
		assertEquals(5, player(score, "me").getPoints());
		assertTrue(player(score, "me").isEstimated());
		// Team total is still exact: MVP points are always awarded to someone
		assertEquals(3 * 3 + 2, score.getTeamPoints());
	}

	@Test
	public void mvpIsEstimatedForAnUnknownTeammateWhenWeTrailTheAverage()
	{
		RaidState raid = raid("me", "a", "b");
		raid.recordHit(Segment.MAIDEN, 250, true);
		raid.recordHit(Segment.MAIDEN, 600, false); // average 300 each
		raid.completeThrough(Segment.MAIDEN);

		PointCalculator.RaidScore score = PointCalculator.calculate(raid);
		assertEquals(2, score.getEstimatedMvpPoints());
		assertEquals(3, player(score, "me").getPoints());
		assertTrue(player(score, "me").isEstimated());
		assertEquals(3 * 3 + 2, score.getTeamPoints());
	}

	@Test
	public void pointsAreExactWhenEveryoneSharesOrMvpIsCertain()
	{
		RaidState raid = raid("me", "a");
		raid.recordHit(Segment.MAIDEN, 700, true);
		raid.recordHit(Segment.MAIDEN, 600, false);
		raid.completeThrough(Segment.MAIDEN);

		PointCalculator.RaidScore score = PointCalculator.calculate(raid);
		assertFalse(player(score, "me").isEstimated());
		// Teammates without data are always estimated, since their MVP points aren't known
		assertTrue(player(score, "a").isEstimated());
	}

	@Test
	public void mvpIsCertainWhenWeOutdamageEverythingElse()
	{
		RaidState raid = raid("me", "a", "b");
		raid.recordHit(Segment.MAIDEN, 700, true);
		raid.recordHit(Segment.MAIDEN, 600, false);
		raid.completeThrough(Segment.MAIDEN);

		PointCalculator.RaidScore score = PointCalculator.calculate(raid);
		assertEquals(0, score.getEstimatedMvpPoints());
		assertEquals(5, player(score, "me").getPoints());
	}

	@Test
	public void personalChanceIsShareOfTeamChance()
	{
		RaidState raid = raid("me", "a");
		raid.setPartyDamage("a", damage(Segment.MAIDEN, 0));
		for (Segment s : Segment.values())
		{
			raid.recordHit(s, 100, true);
		}
		raid.completeThrough(Segment.VERZIK_P3);

		PointCalculator.RaidScore score = PointCalculator.calculate(raid);
		assertEquals(50, score.getTeamPoints());
		assertEquals(32, player(score, "me").getPoints());
		assertEquals(18, player(score, "a").getPoints());
		assertEquals(32.0 / 50, player(score, "me").getShare(), EPSILON);
		assertEquals((1 / 9.1) * 32 / 50, player(score, "me").getUniqueChance(), EPSILON);
	}

	@Test
	public void completingLaterSegmentBackfillsEarlierOnes()
	{
		RaidState raid = raid("me");
		raid.completeThrough(Segment.SOTETSEG);
		assertEquals(4, raid.getRoomsCompleted());
		assertEquals(4, raid.getCurrentRoom());
		raid.completeThrough(Segment.VERZIK_P1);
		assertEquals(5, raid.getRoomsCompleted());
		assertEquals(Segment.VERZIK_P2, raid.getCurrentSegment());
	}

	private static int[] damage(Segment segment, int amount)
	{
		int[] d = new int[Segment.count()];
		d[segment.ordinal()] = amount;
		return d;
	}
}
