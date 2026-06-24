package ru.daniil4jk.ai.deltachat.connector.model;

/**
 * DC_MSG_* — тип содержимого сообщения Delta Chat.
 * <a href="https://github.com/chatmail/core/blob/main/deltachat-ffi/src/lib.rs">Constants reference</a>
 */
public enum MessageViewtype {

    TEXT(0),
    IMAGE(20),
    GIF(21),
    STICKER(23),
    AUDIO(40),
    VOICE(41),
    VIDEO(50),
    FILE(60),
    EVENT(100),
    WEBXDC(110);

    private final int code;

    MessageViewtype(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static MessageViewtype fromCode(int code) {
        for (var v : values()) {
            if (v.code == code) return v;
        }
        return FILE;
    }
}
