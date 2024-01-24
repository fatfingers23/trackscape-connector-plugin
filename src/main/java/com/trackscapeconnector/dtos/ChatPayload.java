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
    @SerializedName("icon_id")
    public final int iconId;
    @SerializedName("is_league_world")
    private final boolean isLeagueWorld;

    private ChatPayload(String clanName, String sender, String message, String rank, int iconId, boolean isLeagueWorld) {
        this.clanName = clanName;
        this.sender = sender;
        this.message = message;
        this.rank = rank;
        this.iconId = iconId;
        this.isLeagueWorld = isLeagueWorld;
    }

    public static ChatPayload from(String clanName, String sender, String message, String rank, int iconId, boolean isLeagueWorld) {
        return new ChatPayload(clanName, sender, message, rank, iconId, isLeagueWorld);
    }
}