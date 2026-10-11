package com.camjewell.tobpointtracker;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.NpcID;

/**
 * Maps ToB boss NPC ids (in all three modes) to the scoring segment damage on them counts towards.
 */
final class TobNpcs
{
	private TobNpcs()
	{
	}

	private static final Map<Integer, Segment> BOSS_SEGMENTS = ImmutableMap.<Integer, Segment>builder()
		// Maiden
		.put(NpcID.TOB_MAIDEN_100, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_70, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_50, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_30, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_100_STORY, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_70_STORY, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_50_STORY, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_30_STORY, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_100_HARD, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_70_HARD, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_50_HARD, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_30_HARD, Segment.MAIDEN)
		// Bloat
		.put(NpcID.TOB_BLOAT, Segment.BLOAT)
		.put(NpcID.TOB_BLOAT_STORY, Segment.BLOAT)
		.put(NpcID.TOB_BLOAT_HARD, Segment.BLOAT)
		// Nylocas Vasilias
		.put(NpcID.NYLOCAS_BOSS_MELEE, Segment.NYLOCAS)
		.put(NpcID.NYLOCAS_BOSS_MAGIC, Segment.NYLOCAS)
		.put(NpcID.NYLOCAS_BOSS_RANGED, Segment.NYLOCAS)
		.put(NpcID.NYLOCAS_BOSS_MELEE_STORY, Segment.NYLOCAS)
		.put(NpcID.NYLOCAS_BOSS_MAGIC_STORY, Segment.NYLOCAS)
		.put(NpcID.NYLOCAS_BOSS_RANGED_STORY, Segment.NYLOCAS)
		.put(NpcID.NYLOCAS_BOSS_MELEE_HARD, Segment.NYLOCAS)
		.put(NpcID.NYLOCAS_BOSS_MAGIC_HARD, Segment.NYLOCAS)
		.put(NpcID.NYLOCAS_BOSS_RANGED_HARD, Segment.NYLOCAS)
		// Sotetseg
		.put(NpcID.TOB_SOTETSEG_COMBAT, Segment.SOTETSEG)
		.put(NpcID.TOB_SOTETSEG_COMBAT_STORY, Segment.SOTETSEG)
		.put(NpcID.TOB_SOTETSEG_COMBAT_HARD, Segment.SOTETSEG)
		// Xarpus
		.put(NpcID.TOB_XARPUS_COMBAT, Segment.XARPUS)
		.put(NpcID.TOB_XARPUS_COMBAT_STORY, Segment.XARPUS)
		.put(NpcID.TOB_XARPUS_COMBAT_HARD, Segment.XARPUS)
		// Verzik
		.put(NpcID.VERZIK_PHASE1, Segment.VERZIK_P1)
		.put(NpcID.VERZIK_PHASE1_STORY, Segment.VERZIK_P1)
		.put(NpcID.VERZIK_PHASE1_HARD, Segment.VERZIK_P1)
		.put(NpcID.VERZIK_PHASE2, Segment.VERZIK_P2)
		.put(NpcID.VERZIK_PHASE2_STORY, Segment.VERZIK_P2)
		.put(NpcID.VERZIK_PHASE2_HARD, Segment.VERZIK_P2)
		.put(NpcID.VERZIK_PHASE3, Segment.VERZIK_P3)
		.put(NpcID.VERZIK_PHASE3_STORY, Segment.VERZIK_P3)
		.put(NpcID.VERZIK_PHASE3_HARD, Segment.VERZIK_P3)
		.build();

	/**
	 * NPC forms that only appear once a segment's boss is dead (or the phase has ended).
	 */
	private static final Map<Integer, Segment> END_FORMS = ImmutableMap.<Integer, Segment>builder()
		.put(NpcID.TOB_MAIDEN_DYING_A, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_DYING_B, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_DYING_A_STORY, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_DYING_B_STORY, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_DYING_A_HARD, Segment.MAIDEN)
		.put(NpcID.TOB_MAIDEN_DYING_B_HARD, Segment.MAIDEN)
		.put(NpcID.XARPUS_DEATH, Segment.XARPUS)
		.put(NpcID.XARPUS_DEATH_STORY, Segment.XARPUS)
		.put(NpcID.XARPUS_DEATH_HARD, Segment.XARPUS)
		.put(NpcID.VERZIK_PHASE1_TO2_TRANSITION, Segment.VERZIK_P1)
		.put(NpcID.VERZIK_PHASE1_TO2_TRANSITION_STORY, Segment.VERZIK_P1)
		.put(NpcID.VERZIK_PHASE1_TO2_TRANSITION_HARD, Segment.VERZIK_P1)
		.put(NpcID.VERZIK_PHASE2_TO3_TRANSITION, Segment.VERZIK_P2)
		.put(NpcID.VERZIK_PHASE2_TO3_TRANSITION_STORY, Segment.VERZIK_P2)
		.put(NpcID.VERZIK_PHASE2_TO3_TRANSITION_HARD, Segment.VERZIK_P2)
		.put(NpcID.VERZIK_DEATH_BAT, Segment.VERZIK_P3)
		.put(NpcID.VERZIK_DEATH_BAT_STORY, Segment.VERZIK_P3)
		.put(NpcID.VERZIK_DEATH_BAT_HARD, Segment.VERZIK_P3)
		.build();

	private static final Set<Integer> ENTRY_MODE_BOSSES = ImmutableSet.of(
		NpcID.TOB_MAIDEN_100_STORY, NpcID.TOB_MAIDEN_70_STORY, NpcID.TOB_MAIDEN_50_STORY, NpcID.TOB_MAIDEN_30_STORY,
		NpcID.TOB_BLOAT_STORY,
		NpcID.NYLOCAS_BOSS_SPAWNING_STORY, NpcID.NYLOCAS_BOSS_MELEE_STORY, NpcID.NYLOCAS_BOSS_MAGIC_STORY,
		NpcID.NYLOCAS_BOSS_RANGED_STORY,
		NpcID.TOB_SOTETSEG_NONCOMBAT_STORY, NpcID.TOB_SOTETSEG_COMBAT_STORY,
		NpcID.TOB_XARPUS_STATIC_STORY, NpcID.TOB_XARPUS_COMBAT_STORY,
		NpcID.VERZIK_INITIAL_STORY, NpcID.VERZIK_PHASE1_STORY, NpcID.VERZIK_PHASE2_STORY, NpcID.VERZIK_PHASE3_STORY);

	private static final Set<Integer> HARD_MODE_BOSSES = ImmutableSet.of(
		NpcID.TOB_MAIDEN_100_HARD, NpcID.TOB_MAIDEN_70_HARD, NpcID.TOB_MAIDEN_50_HARD, NpcID.TOB_MAIDEN_30_HARD,
		NpcID.TOB_BLOAT_HARD,
		NpcID.NYLOCAS_BOSS_SPAWNING_HARD, NpcID.NYLOCAS_BOSS_MELEE_HARD, NpcID.NYLOCAS_BOSS_MAGIC_HARD,
		NpcID.NYLOCAS_BOSS_RANGED_HARD,
		NpcID.TOB_SOTETSEG_NONCOMBAT_HARD, NpcID.TOB_SOTETSEG_COMBAT_HARD,
		NpcID.TOB_XARPUS_STATIC_HARD, NpcID.TOB_XARPUS_COMBAT_HARD,
		NpcID.VERZIK_INITIAL_HARD, NpcID.VERZIK_PHASE1_HARD, NpcID.VERZIK_PHASE2_HARD, NpcID.VERZIK_PHASE3_HARD);

	private static final Set<Integer> NORMAL_MODE_BOSSES = ImmutableSet.of(
		NpcID.TOB_MAIDEN_100, NpcID.TOB_MAIDEN_70, NpcID.TOB_MAIDEN_50, NpcID.TOB_MAIDEN_30,
		NpcID.TOB_BLOAT,
		NpcID.NYLOCAS_BOSS_SPAWNING, NpcID.NYLOCAS_BOSS_MELEE, NpcID.NYLOCAS_BOSS_MAGIC, NpcID.NYLOCAS_BOSS_RANGED,
		NpcID.TOB_SOTETSEG_NONCOMBAT, NpcID.TOB_SOTETSEG_COMBAT,
		NpcID.TOB_XARPUS_STATIC, NpcID.TOB_XARPUS_COMBAT,
		NpcID.VERZIK_INITIAL, NpcID.VERZIK_PHASE1, NpcID.VERZIK_PHASE2, NpcID.VERZIK_PHASE3);

	/**
	 * @return the segment damage on this NPC counts towards, or null if it isn't a boss
	 */
	static Segment bossSegment(int npcId)
	{
		return BOSS_SEGMENTS.get(npcId);
	}

	/**
	 * @return the segment that is over once an NPC takes this form, or null
	 */
	static Segment endedSegment(int npcId)
	{
		return END_FORMS.get(npcId);
	}

	/**
	 * @return the raid mode implied by a boss NPC id, or null if it isn't a boss
	 */
	static RaidMode modeOf(int npcId)
	{
		if (ENTRY_MODE_BOSSES.contains(npcId))
		{
			return RaidMode.ENTRY;
		}
		if (HARD_MODE_BOSSES.contains(npcId))
		{
			return RaidMode.HARD;
		}
		if (NORMAL_MODE_BOSSES.contains(npcId))
		{
			return RaidMode.NORMAL;
		}
		return null;
	}
}
