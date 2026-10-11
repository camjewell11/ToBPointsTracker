package com.camjewell.tobpointtracker;

import com.google.gson.annotations.SerializedName;
import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Broadcast to the RuneLite party after each segment so teammates can work out who earned each MVP. It carries the
 * sender's full per-segment boss damage for the raid, so a missed message is fixed by the next one.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class ToBDamageUpdate extends PartyMemberMessage
{
	/**
	 * In-game name of the sender, used to match them to a ToB party slot.
	 */
	@SerializedName("n")
	String name;
	/**
	 * Boss damage per {@link Segment}, indexed by ordinal.
	 */
	@SerializedName("d")
	int[] damage;
}
