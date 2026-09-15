package com.osrssniffin.threechoices;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ThreeChoicesPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ThreeChoicesPlugin.class);
        RuneLite.main(args);
    }
}
