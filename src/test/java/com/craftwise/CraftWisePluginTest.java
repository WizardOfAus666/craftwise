package com.craftwise;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Developer launcher that boots RuneLite with {@link CraftWisePlugin} loaded.
 */
public class CraftWisePluginTest
{
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CraftWisePlugin.class);
		RuneLite.main(args);
	}
}
