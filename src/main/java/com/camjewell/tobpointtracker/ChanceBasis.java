package com.camjewell.tobpointtracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Which team points the unique chance is worked out from during a raid. Both agree once Verzik is dead.
 */
@Getter
@RequiredArgsConstructor
public enum ChanceBasis
{
	/**
	 * Starts at the full rate and only drops on deaths, assuming the rest of the raid is cleared without dying.
	 */
	PROJECTED("Projected"),
	/**
	 * Starts at 0 and climbs as points are earned, like the CoX and ToA trackers.
	 */
	CURRENT("Current");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
