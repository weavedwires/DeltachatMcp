package ru.daniil4jk.ai.deltachat.connector.model;

public enum MessageViewtype {
    UNKNOWN, TEXT, IMAGE, GIF, STICKER, AUDIO, VOICE, VIDEO, FILE, CALL, WEBXDC, VCARD;

    public static MessageViewtype fromString(String s) {
        if (s == null) return FILE;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FILE;
        }
    }
}
