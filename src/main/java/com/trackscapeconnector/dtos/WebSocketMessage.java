package com.trackscapeconnector.dtos;

import com.google.gson.annotations.SerializedName;
import lombok.ToString;


@ToString
public class WebSocketMessage<T> {

    public enum MessageType {
        ToClanChat,
        ToDiscord
    }

    @SerializedName("message_type")
    public final MessageType message_type;

    public final T message;

    private WebSocketMessage(MessageType message_type, T message) {
        this.message_type = message_type;
        this.message = message;
    }

}
