package com.trackscapeconnector;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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


    @ConfigSection(
            name = "Advance Settings",
            description = "Settings for setting up your own TrackScape API.",
            position = 3,
            closedByDefault = true
    )
    String advanceSettings = "advancesettings";

    @ConfigItem(
            keyName = "httpApiEndpoint",
            name = "URL for sending Clan Chats",
            description = "Host your own TrackScape API and enter the URL here.",
            section = advanceSettings
    )
    default String httpApiEndpoint() {
        return "https://bot.trackscape.app/api/chat/new-clan-chat";
    }

    @ConfigItem(
            keyName = "wsEndpoint",
            name = "Websocket url for receiving Discord messages",
            description = "Host your own TrackScape API and enter the URL here for WebSockets.",
            section = advanceSettings
    )
    default String webSocketEndpoint() {
        return "wss://bot.trackscape.app/api/chat/ws";
    }
}
