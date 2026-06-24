package ru.daniil4jk.ai.deltachat.connector.service;

/**
 * Исключение, которое выбрасывается при попытке получить последние сообщения
 * из чата, в котором есть непрочитанные.
 * <p>
 * Агент должен сначала вызвать {@link DeltachatService#getUnreadMessages(int)}.
 */
public class UnreadMessagesExistException extends RuntimeException {

    private final int chatId;
    private final int unreadCount;

    public UnreadMessagesExistException(int chatId, int unreadCount) {
        super("Chat " + chatId + " has " + unreadCount + " unread messages; call get_unread_messages first");
        this.chatId = chatId;
        this.unreadCount = unreadCount;
    }

    public int getChatId() {
        return chatId;
    }

    public int getUnreadCount() {
        return unreadCount;
    }
}
