package com.trackscapeconnector;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TrackScapeConnectorPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TrackScapeConnectorPlugin.class);
		RuneLite.main(args);
	}
}