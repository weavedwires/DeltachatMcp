package ru.daniil4jk.ai.deltachat.connector.model;

import lombok.Data;

import java.util.List;

/**
 * Полное отражение JSON-RPC объекта FullChat из Delta Chat.
 * <p>
 * Возвращается методами {@code getChatlistEntries}, {@code getBasicChatInfo} и т.д.
 *
 * @see <a href="https://github.com/chatmail/core/blob/main/deltachat-jsonrpc/src/api/types/chat.rs">Rust struct FullChat</a>
 */
@Data
public class Chat {

    /** Уникальный ID чата. */
    private int id;

    /** Название чата. */
    private String name;

    /** Подзаголовок/статус чата. */
    private String subtitle;

    /** Тип чата (одна из констант DC_CHAT_*: 100=single, 120=group, 130=mailing list, 140=broadcast). */
    private int type;

    /** Статус чата (DC_CHAT_STATUS_*). */
    private int status;

    /** Цвет чата (hex-строка). */
    private String color;

    /** ID контактов в чате. */
    private List<Integer> contactIds;

    /** Количество свежих (непрочитанных) сообщений. */
    private int freshMessageCount;

    /** Количество непрочитанных сообщений (счётчик). */
    private int unreadMessagesCounter;

    /** Таймер самоуничтожения (секунды). */
    private int ephemeralTimer;

    /** Время последнего обновления (unix-таймстамп). */
    private long lastUpdated;

    /** Путь к аватару (blob-директория). */
    private String avatar;

    /** Путь к profile image (большой аватар). */
    private String profileImage;

    // Флаги:

    private boolean archived;
    private boolean isContactRequest;
    private boolean isDeviceChat;
    private boolean isMailingList;
    private boolean isMuted;
    private boolean isProtected;
    private boolean isSelfTalk;
    private boolean isSupportChat;
    private boolean isUnpromoted;
    private boolean selfInGroup;
}
