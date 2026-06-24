package ru.daniil4jk.ai.deltachat.connector.model;

/**
 * DC_STATE_* — статус сообщения Delta Chat.
 */
public final class MessageState {

    private MessageState() {
    }

    public static final int UNDEFINED = 0;
    public static final int IN_FRESH = 10;     // непрочитано входящее
    public static final int IN_SEEN = 13;      // прочитано входящее
    public static final int IN_NOTICED = 15;   // замечено входящее
    public static final int OUT_DRAFT = 19;    // черновик
    public static final int OUT_PENDING = 20;  // отправляется
    public static final int OUT_FAILED = 24;   // ошибка отправки
    public static final int OUT_DELIVERED = 26; // доставлено
    public static final int OUT_MDN_RCVD = 28; // прочитано получателем
    public static final int OUT_DELETED = 30;  // удалено
}
