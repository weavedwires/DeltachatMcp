package ru.daniil4jk.ai.deltachat.connector.service;

import ru.daniil4jk.ai.deltachat.connector.model.Chat;
import ru.daniil4jk.ai.deltachat.connector.model.InputMessage;
import ru.daniil4jk.ai.deltachat.connector.model.OutputMessage;
import java.util.List;

/**
 * Сервисный интерфейс — абстракция над JSON-RPC вызовами к deltachat-rpc-server.
 * <p>
 * Все методы работают с фиксированным accountId (первый аккаунт в конфиге).
 * Имплементация будет запускать deltachat-rpc-server как подпроцесс и
 * общаться с ним через jsonrpc4j по stdin/stdout.
 */
public interface DeltachatService {

    /**
     * Список чатов с фильтром {@code listFlags} и опциональным поиском {@code query}.
     * <p>
     * Флаги: 0 = все, 9 = архивные, 512 = без групп (только 1:1).
     */
    List<Chat> listChats(int listFlags, String query);

    /**
     * Чаты c непрочитанными сообщениями (freshMessageCount > 0).
     * <p>
     * Краткая форма: список-всех + фильтр.
     */
    List<Chat> listUnreadChats();

    /**
     * Получить первые {@code n} непрочитанных сообщений (начиная с курсора)
     * и пометить их прочитанными.
     * <p>
     * Сообщения собираются из всех чатов, сортируются по timestampSort,
     * возвращаются первые n.
     *
     * @param n максимальное количество сообщений
     * @return список непрочитанных сообщений (не более n)
     */
    List<OutputMessage> getUnreadMessages(int n);

    /**
     * Получить последние {@code n} сообщений из чата {@code chatId}
     * и пометить их прочитанными.
     *
     * @param chatId ID чата
     * @param n      количество последних сообщений
     * @return список сообщений
     * @throws UnreadMessagesExistException если в чате есть непрочитанные — чтобы
     *                                      подсказать агенту сначала вызвать {@link #getUnreadMessages(int)}
     */
    List<OutputMessage> getLastMessages(int chatId, int n);

    /**
     * Получить сообщение по его ID.
     *
     * @param msgId ID сообщения
     * @return сообщение
     */
    OutputMessage getMessage(int msgId);

    /**
     * Отправить сообщение ({@link InputMessage} — только поля для отправки).
     *
     * @param chatId ID чата
     * @param msg    параметры отправки (text, html, file, quotedMessageId, location, …)
     * @return ID отправленного сообщения
     */
    int sendMessage(int chatId, InputMessage msg);
}
