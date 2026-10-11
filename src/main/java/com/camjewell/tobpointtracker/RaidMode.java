package com.camjewell.tobpointtracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RaidMode
{
	// Entry Mode can't roll uniques at all; points are still tracked for practice.
	ENTRY("Entry", 0),
	NORMAL("Normal", 1 / 9.1),
	HARD("Hard", 1 / 7.7);

	private final String displayName;
	/**
	 * Team unique chance for a perfect (max points) raid.
	 */
	private final double baseUniqueChance;

	public boolean canRollUniques()
	{
		return baseUniqueChance > 0;
	}

	static RaidMode fromChatName(String name)
	{
		switch (name.toLowerCase())
		{
			case "entry":
			case "story":
				return ENTRY;
			case "hard":
				return HARD;
			default:
				return NORMAL;
		}
	}
}
