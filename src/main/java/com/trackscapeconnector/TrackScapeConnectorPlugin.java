package com.trackscapeconnector;

import com.google.gson.Gson;
import com.google.inject.Provides;

import javax.inject.Inject;

import com.trackscapeconnector.dtos.ChatPayload;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.IconID;
import net.runelite.api.IndexedSprite;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;
import net.runelite.api.clan.ClanID;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
        name = "TrackScape Connector"
)
public class TrackScapeConnectorPlugin extends Plugin {
    private static final int NORMAL_CLOSURE_STATUS = 1000;
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
    private static final String CLAN_WELCOME_TEXT = "To talk in your clan's channel, start each line of chat with // or /c.";
    private RemoteSubmitter remoteSubmitter;
    private WebSocketListener webSocketListener;
    public WebSocket ws;
    private int discordIconLocation = -1;
    private String iconImg;
    private static final Pattern ICON_PATTERN = Pattern.compile("<img=(\\d+)>");


    @Override
    protected void startUp() throws Exception {
        clientThread.invoke(() ->
        {
            if (client.getModIcons() == null) {
                return false;
            }
            loadIcon();
            return true;
        });
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
                log.debug("Sender: " + event.getName());
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

                int iconId = IconID.NO_ENTRY.getIndex();
                var matcher = ICON_PATTERN.matcher(event.getName());
                if (matcher.find()) {
                    iconId = Integer.parseInt(matcher.group(1));
                }

                var rank = GetMembersTitle(sender, clanChannel.getName());
                ChatPayload chatPayload = ChatPayload.from(clanChannel.getName(), sender, event.getMessage(), rank, iconId);
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
            if (config.allowMessagesFromDiscord()) {
                if (webSocketListener == null) {
                    startWebsocket(config.webSocketEndpoint());
                }
            } else {
                if (webSocketListener != null) {
                    stopWebsocket();
                }
            }
        }
    }

    @Subscribe
    public void onClanChannelChanged(ClanChannelChanged event) {

        if (event.getClanId() == ClanID.CLAN) {
            if (event.getClanChannel() == null) {
                if (remoteSubmitter != null) {
                    shutdownRemoteSubmitter();
                    stopWebsocket();
                }
                if (webSocketListener != null) {
                    stopWebsocket();
                }
            } else {
                if (config.allowMessagesFromDiscord()) {
                    if (webSocketListener == null) {
                        startWebsocket(config.webSocketEndpoint());
                    }
                }

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
        remoteSubmitter = RemoteSubmitter.create(httpClient, gson, config.httpApiEndpoint(), config.verificationCode());
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

    public void startWebsocket(String host) {
        log.debug("Connecting...");
        Request request = new Request.Builder().url(host)
                .addHeader("verification-code", config.verificationCode())
                .build();

        webSocketListener = new com.trackscapeconnector.WebSocketListener(client, clientThread, httpClient, gson, discordIconLocation);
        ws = httpClient.newWebSocket(request, webSocketListener);
    }

    public void stopWebsocket() {
        ws.close(NORMAL_CLOSURE_STATUS, null);
        webSocketListener = null;
    }

    private void loadIcon() {

        final IndexedSprite[] modIcons = client.getModIcons();

        if (discordIconLocation != -1 || modIcons == null) {
            return;
        }

        BufferedImage image = ImageUtil.loadImageResource(getClass(), "/discord-mark-blue-smaller.png");
        IndexedSprite indexedSprite = ImageUtil.getImageIndexedSprite(image, client);
        discordIconLocation = modIcons.length;

        final IndexedSprite[] newModIcons = Arrays.copyOf(modIcons, modIcons.length + 1);
        newModIcons[newModIcons.length - 1] = indexedSprite;

        client.setModIcons(newModIcons);

    }
}
