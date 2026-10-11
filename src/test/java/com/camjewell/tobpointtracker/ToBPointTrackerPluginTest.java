package com.camjewell.tobpointtracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ToBPointTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ToBPointTrackerPlugin.class);
		RuneLite.main(args);
	}
}
