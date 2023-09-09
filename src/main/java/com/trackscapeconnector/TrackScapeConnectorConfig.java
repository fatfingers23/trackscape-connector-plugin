package com.trackscapeconnector;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("trackScapeconnectorplugin")
public interface TrackScapeConnectorConfig extends Config {
    @ConfigItem(
            keyName = "verificationcode",
            name = "Verification Code",
            description = "The code given to you by the TrackScape bot or a Clan Leader that verifies your connection to the clan."
    )
    default String verificationCode() {
        return "";
    }
}
