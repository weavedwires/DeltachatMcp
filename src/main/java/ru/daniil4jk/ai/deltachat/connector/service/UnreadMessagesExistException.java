package ru.daniil4jk.ai.deltachat.connector.service;

import lombok.Getter;

/**
 * Исключение, которое выбрасывается при попытке получить последние сообщения
 * из чата, в котором есть непрочитанные.
 * <p>
 * Агент должен сначала вызвать {@link DeltachatService#getUnreadMessages(int, int)}.
 */
@Getter
public class UnreadMessagesExistException extends RuntimeException {

    private final int chatId;
    private final int unreadCount;

    public UnreadMessagesExistException(int chatId, int unreadCount) {
        super("Chat " + chatId + " has " + unreadCount + " unread messages; call get_unread_messages(chatId=" + chatId + ", n=" + unreadCount + ") first");
        this.chatId = chatId;
        this.unreadCount = unreadCount;
    }
}
