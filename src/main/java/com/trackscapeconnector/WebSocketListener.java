package com.trackscapeconnector;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.trackscapeconnector.dtos.DiscordChat;
import com.trackscapeconnector.dtos.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.WebSocket;

import java.lang.reflect.Type;

@Slf4j
public final class WebSocketListener extends okhttp3.WebSocketListener {

    private ClientThread clientThread;
    private Client client;

    public boolean socketConnected;

    public boolean socketConnecting = true;

    private static final int NORMAL_CLOSURE_STATUS = 1000;

    private final OkHttpClient okHttpClient;

    public WebSocket ws;

    private final Gson gson;
    private final int discordIconLocation;
    Type WebSocketDiscordMessage = new TypeToken<WebSocketMessage<DiscordChat>>() {
    }.getType();

    public WebSocketListener(Client gameClient, ClientThread gameClientThread, OkHttpClient okHttpClient, Gson gson, int DiscordIconLocation) {
        this.client = gameClient;
        this.clientThread = gameClientThread;
        this.okHttpClient = okHttpClient;
        this.gson = gson;
        this.discordIconLocation = DiscordIconLocation;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        log.debug("Connected");
        socketConnecting = false;
        socketConnected = true;
    }

    @Override
    public void onMessage(WebSocket webSocket, String jsonString) {
        var data = gson.fromJson(jsonString, WebSocketMessage.class);
        if (data.message_type == WebSocketMessage.MessageType.ToClanChat) {
            WebSocketMessage<DiscordChat> discordMessage = gson.fromJson(jsonString, WebSocketDiscordMessage);
            handleDiscordChat(discordMessage.message);
        }

    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        webSocket.close(NORMAL_CLOSURE_STATUS, null);
        log.debug("Closing: " + code + " " + reason);
        socketConnecting = false;
        socketConnected = false;
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        t.printStackTrace();
        socketConnecting = false;
        socketConnected = false;
    }

    private void handleDiscordChat(DiscordChat message) {
        clientThread.invokeLater(() -> {
            var clanName = client.getClanChannel().getName();
            var sender = String.format("<img=%d>%s", discordIconLocation, Text.toJagexName(message.sender));
            client.addChatMessage(ChatMessageType.CLAN_CHAT, sender, message.message, clanName, false);
        });
    }

}
