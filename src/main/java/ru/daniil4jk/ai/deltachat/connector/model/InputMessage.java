package ru.daniil4jk.ai.deltachat.connector.model;

import lombok.Data;

/**
 * Параметры отправки сообщения — аналог {@code MessageData} из DC JSON-RPC.
 * <p>
 * Включает только поля, которые принимает метод {@code sendMsg}.
 * Отсутствующие / null поля игнорируются.
 *
 * @see <a href="https://github.com/chatmail/core/blob/main/deltachat-jsonrpc/src/api/types/message.rs">Rust struct MessageData</a>
 */
@Data
public class InputMessage {

    /** Текст сообщения. */
    private String text;

    /** HTML-версия текста. */
    private String html;

    /** Путь к файлу в blob-директории. */
    private String file;

    /** ID цитируемого сообщения. */
    private Integer quotedMessageId;

    /** Координаты геопозиции. */
    private Location location;

    /** Переопределённое имя отправителя. */
    private String overrideSenderName;
}
