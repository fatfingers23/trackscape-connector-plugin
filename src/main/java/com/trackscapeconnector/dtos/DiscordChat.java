package com.trackscapeconnector.dtos;

import lombok.ToString;

@ToString
public class DiscordChat {

    public final String sender;
    public final String message;

    public DiscordChat(String sender, String message) {
        this.sender = sender;
        this.message = message;
    }
}
