package com.trackscapeconnector;

import com.google.gson.Gson;
import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;
import net.runelite.api.clan.ClanID;

import java.util.Objects;

@Slf4j
@PluginDescriptor(
        name = "TrackScape Connector"
)
public class TrackScapeConnectorPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private TrackScapeConnectorConfig config;

    @Inject
    private OkHttpClient httpClient;

    @Inject
    private Gson gson;

    private static final String BASE_API_ENDPOINT = "http://localhost:8001";
    private static final String CLAN_WELCOME_TEXT = "To talk in your clan's channel, start each line of chat with // or /c.";

    private RemoteSubmitter remoteSubmitter;

    @Override
    protected void startUp() throws Exception {
    }

    @Override
    protected void shutDown() throws Exception {
    }
    
    @Provides
    TrackScapeConnectorConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TrackScapeConnectorConfig.class);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        switch (event.getType()) {

            case CLAN_CHAT:
            case CLAN_MESSAGE:
                ClanChannel clanChannel = client.getClanChannel();
                String sender = "";
                if (event.getType() == ChatMessageType.CLAN_MESSAGE) {
                    sender = clanChannel.getName();
                } else {
                    sender = Text.removeFormattingTags(Text.toJagexName(event.getName()));
                }
                if (Objects.equals(sender, clanChannel.getName())) {
                    if (event.getMessage().equals(CLAN_WELCOME_TEXT)) {
                        break;
                    }
                }

                var rank = GetMembersTitle(sender, clanChannel.getName());
                ChatPayload chatPayload = ChatPayload.from(clanChannel.getName(), sender, event.getMessage(), rank);
                remoteSubmitter.queue(chatPayload);
                break;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getGroup().equals("trackScapeconnectorplugin")) {
            if (remoteSubmitter != null) {
                shutdownRemoteSubmitter();
            }
            startRemoteSubmitter();
        }
    }

    @Subscribe
    public void onClanChannelChanged(ClanChannelChanged event) {

        if (event.getClanId() == ClanID.CLAN) {
            if (event.getClanChannel() == null) {
                if (remoteSubmitter != null) {
                    shutdownRemoteSubmitter();
                }
            } else {
                if (remoteSubmitter == null) {
                    startRemoteSubmitter();
                }
            }
        }
    }

    private void startRemoteSubmitter() {

        if (remoteSubmitter != null) {
            log.debug("Shutting down previous remoteSubmitter...");
            shutdownRemoteSubmitter();
        }

        log.debug("Starting a new remoteSubmitter...");
        remoteSubmitter = RemoteSubmitter.create(httpClient, gson, BASE_API_ENDPOINT, config.verificationCode());
        remoteSubmitter.initialize();
    }

    private void shutdownRemoteSubmitter() {
        if (remoteSubmitter != null) {
            remoteSubmitter.shutdown();
            remoteSubmitter = null;
        }
    }

    private String GetMembersTitle(String name, String clanName) {
        String cleanName = Text.removeTags(name);
        ClanChannel clanChannel = client.getClanChannel();
        if (clanChannel != null) {
            ClanChannelMember member = clanChannel.findMember(cleanName);
            if (member != null && clanChannel.getName().equals(clanName)) {
                var rank = member.getRank();
                var clanSettings = client.getClanSettings();
                if (clanSettings != null) {
                    var clanTitle = clanSettings.titleForRank(rank).getName();
                    if (clanTitle != null) {
                        return clanTitle;
                    }
                }

            }
        }

        return "Guest";
    }
}
