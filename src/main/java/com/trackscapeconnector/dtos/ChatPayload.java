package com.trackscapeconnector.dtos;

import com.google.gson.annotations.SerializedName;
import lombok.ToString;


@ToString
public class ChatPayload {

    @SerializedName("clan_name")
    public final String clanName;
    public final String sender;
    public final String message;
    public final String rank;

    private ChatPayload(String clanName, String sender, String message, String rank) {
        this.clanName = clanName;
        this.sender = sender;
        this.message = message;
        this.rank = rank;
    }

    public static ChatPayload from(String clanName, String sender, String message, String rank) {
        return new ChatPayload(clanName, sender, message, rank);
    }
}