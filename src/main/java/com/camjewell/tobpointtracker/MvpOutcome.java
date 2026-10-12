package com.camjewell.tobpointtracker;

/**
 * What can be proven about whether a player earned a segment's MVP points.
 */
public enum MvpOutcome
{
	WON,
	LOST,
	/**
	 * Teammates without the plugin might have out-damaged them, or might not.
	 */
	UNKNOWN
}
