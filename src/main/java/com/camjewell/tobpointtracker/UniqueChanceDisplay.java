package com.camjewell.tobpointtracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UniqueChanceDisplay
{
	OFF("Off"),
	PERSONAL_CHANCE("Personal"),
	TEAM_CHANCE("Team"),
	BOTH("Both");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
