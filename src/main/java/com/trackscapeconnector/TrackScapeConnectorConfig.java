package com.trackscapeconnector;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("trackScapeconnectorplugin")
public interface TrackScapeConnectorConfig extends Config {
    @ConfigItem(
            keyName = "verificationcode",
            name = "Verification Code",
            description = "The code given to you by the TrackScape bot or a Clan Leader that verifies your connection to the clan.",
            position = 1
    )
    default String verificationCode() {
        return "";
    }

    @ConfigItem(
            keyName = "allowMessagesFromDiscord",
            name = "Receive Messages From Discord",
            description = "If checked you will receive messages from Discord in the chat window.",
            position = 2
    )
    default boolean allowMessagesFromDiscord() {
        return true;
    }

    @ConfigItem(
            keyName = "httpApiEndpoint",
            name = "URL Base for the API",
            description = "Host your own TrackScape API and enter the URL here.",
            hidden = false
    )
    default String httpApiEndpoint() {
        return "http://localhost:8001";
    }

    @ConfigItem(
            keyName = "wsEndpoint",
            name = "URL Base for Websockets",
            description = "Host your own TrackScape API and enter the URL here for WebSockets.",
            hidden = false
    )
    default String webSocketEndpoint() {
        return "ws://127.0.0.1:8001";
    }
}
