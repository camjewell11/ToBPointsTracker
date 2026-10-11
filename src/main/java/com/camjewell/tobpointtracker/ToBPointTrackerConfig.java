package com.camjewell.tobpointtracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(ToBPointTrackerConfig.GROUP)
public interface ToBPointTrackerConfig extends Config
{
	String GROUP = "tobpointtracker";

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show team and personal points while in the Theatre of Blood",
		position = 0
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pointsPercent",
		name = "Display points percent",
		description = "Display each player's points as a percent of the team's points, which is their share of the loot roll",
		position = 1
	)
	default boolean pointsPercent()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPlayers",
		name = "Display teammates",
		description = "Display the points of teammates who share their damage through your RuneLite party with this plugin."
			+ " Other teammates are only counted in Total. Teammates with deaths are shown in red",
		position = 2
	)
	default boolean showPlayers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "uniqueChance",
		name = "Display the chance of a unique",
		description = "Display the chance of a purple: your personal chance, the team's chance, or both as \"personal (team)\"",
		position = 3
	)
	default UniqueChanceDisplay uniqueChance()
	{
		return UniqueChanceDisplay.BOTH;
	}

	@ConfigItem(
		keyName = "chanceBasis",
		name = "Unique chance basis",
		description = "Projected: starts at the full chance and only drops when someone dies, assuming the rest of the raid"
			+ " is cleared without dying. Current: starts at 0% and climbs as rooms are completed, like the CoX and ToA"
			+ " trackers. Both end at the same value once Verzik is dead.",
		position = 4
	)
	default ChanceBasis chanceBasis()
	{
		return ChanceBasis.PROJECTED;
	}

	@ConfigItem(
		keyName = "shareDamage",
		name = "Share with party",
		description = "Send your boss damage to your RuneLite party so teammates running this plugin can work out MVP points."
			+ " Without it, only team points (and your own room points and deaths) are exact.",
		position = 5
	)
	default boolean shareDamage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatSummary",
		name = "Raid summary in chat",
		description = "Post your points and purple chance to the chatbox when Verzik dies",
		position = 6
	)
	default boolean chatSummary()
	{
		return true;
	}
}
