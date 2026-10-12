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
		description = "Show deaths and purple chances while in the Theatre of Blood",
		position = 0
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPlayers",
		name = "Display teammates",
		description = "Display the purple chance of teammates who share their damage through your RuneLite party with this"
			+ " plugin. Players with deaths are shown in red",
		position = 1
	)
	default boolean showPlayers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "uniqueChance",
		name = "Display the chance of a unique",
		description = "Display your personal purple chance, the team's, or both. Your chance is a range while some of your"
			+ " MVP points can't be proven because teammates aren't sharing their damage",
		position = 2
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
		position = 3
	)
	default ChanceBasis chanceBasis()
	{
		return ChanceBasis.PROJECTED;
	}

	@ConfigItem(
		keyName = "shareDamage",
		name = "Share with party",
		description = "Send your boss damage to your RuneLite party so teammates running this plugin can work out MVP points."
			+ " Without everyone sharing, your chance may be a range rather than exact.",
		position = 4
	)
	default boolean shareDamage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatSummary",
		name = "Raid summary in chat",
		description = "Post deaths and purple chances to the chatbox when Verzik dies",
		position = 5
	)
	default boolean chatSummary()
	{
		return true;
	}
}
