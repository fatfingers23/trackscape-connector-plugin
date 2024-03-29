package com.trackscapeconnector;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.trackscapeconnector.dtos.ChatPayload;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanID;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
        name = "TrackScape Connector"
)
public class TrackScapeConnectorPlugin extends Plugin {
    private static final int NORMAL_CLOSURE_STATUS = 1000;
    private static final int WS_RECONNECT_CHECK_INTERVAL = 2;
    private static final Pattern ICON_PATTERN = Pattern.compile("<img=(\\d+)>");
    private static final String CLAN_WELCOME_TEXT = "To talk in your clan's channel, start each line of chat with // or /c.";
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
    @Inject
    private WorldService worldService;
    private RemoteSubmitter remoteSubmitter;
    private WebSocketListener webSocketListener;
    public WebSocket ws;
    private int discordIconLocation = -1;
    private ScheduledExecutorService wsExecutorService;
    private FindWordTypeService findWordTypeService;

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
        startRemoteSubmitter();
        findWordTypeService = new FindWordTypeService(worldService, client);
    }

    @Override
    protected void shutDown() throws Exception {
        stopWebsocket();
        shutdownRemoteSubmitter();
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
                if (Objects.equals(config.verificationCode(), "") || config.verificationCode() == null) {
                    break;
                }

                if (Objects.equals(event.getName(), "") && Objects.equals(event.getSender(), "")) {
                    break;
                }

                ClanChannel clanChannel = client.getClanChannel();
                String sender = "";
                var isLeagueWorld = false;
                if (event.getType() == ChatMessageType.CLAN_MESSAGE) {
                    sender = clanChannel.getName();
                } else {
                    sender = Text.removeFormattingTags(Text.toJagexName(event.getName()));
                }
                if (Objects.equals(sender, clanChannel.getName())) {
                    if (event.getMessage().equals(CLAN_WELCOME_TEXT)) {
                        break;
                    }
                    isLeagueWorld = findWordTypeService.isPlayerInLeaguesWorld(event.getMessage());
                }

                int iconId = IconID.NO_ENTRY.getIndex();
                var matcher = ICON_PATTERN.matcher(event.getName());
                if (matcher.find()) {
                    iconId = Integer.parseInt(matcher.group(1));
                }

                var rank = GetMembersTitle(sender, clanChannel.getName());
                ChatPayload chatPayload = ChatPayload.from(clanChannel.getName(), sender, event.getMessage(), rank, iconId, isLeagueWorld);
                remoteSubmitter.queue(chatPayload);
                break;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getGroup().equals("trackScapeconnectorplugin")) {

            shutdownRemoteSubmitter();
            startRemoteSubmitter();
            stopWebsocket();

            if (!config.allowMessagesFromDiscord()) {
                startWebsocket(config.webSocketEndpoint());
            }
        }
    }

    @Subscribe
    public void onClanChannelChanged(ClanChannelChanged event) {

        if (event.getClanId() == ClanID.CLAN) {
            findWordTypeService.loadClanMembers();
            if (event.getClanChannel() == null) {
                shutdownRemoteSubmitter();
                stopWebsocket();
            } else {
                if (config.allowMessagesFromDiscord()) {
                    stopWebsocket();
                    startWebsocket(config.webSocketEndpoint());
                }
                remoteSubmitter = null;
                startRemoteSubmitter();
            }
        }
    }

    @Subscribe
    public void onClanMemberJoined(ClanMemberJoined event) {

        String channelName = event.getClanChannel().getName();
        if (!Objects.equals(channelName, client.getClanChannel().getName())) {
            return;
        }
        findWordTypeService.CLAN_MEMBER_NAMES.add(event.getClanMember().getName());
    }

    @Subscribe
    public void onClanMemberLeft(ClanMemberLeft event) {

        String channelName = event.getClanChannel().getName();
        if (!Objects.equals(channelName, client.getClanChannel().getName())) {
            return;
        }
        findWordTypeService.CLAN_MEMBER_NAMES.remove(event.getClanMember().getName());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
            shutdownRemoteSubmitter();
            stopWebsocket();
        }
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
            findWordTypeService.loadWorldTypes();
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
                    var titleForRank = clanSettings.titleForRank(rank);
                    if (titleForRank == null) {
                        return "Not Ranked";
                    }
                    return titleForRank.getName();
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
        wsExecutorService = Executors.newSingleThreadScheduledExecutor();
        wsExecutorService.scheduleAtFixedRate(this::checkWebSocketConnection, WS_RECONNECT_CHECK_INTERVAL, WS_RECONNECT_CHECK_INTERVAL, TimeUnit.MINUTES);
    }

    public void stopWebsocket() {
        if (ws != null) {
            ws.close(NORMAL_CLOSURE_STATUS, null);
            if (wsExecutorService != null) {
                wsExecutorService.shutdown();
            }
            wsExecutorService = null;
        }
        webSocketListener = null;
    }

    private void checkWebSocketConnection() {
        if (webSocketListener.socketConnected || webSocketListener.socketConnecting) {
            return;
        }
        stopWebsocket();
        startWebsocket(config.webSocketEndpoint());
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
