package ru.daniil4jk.ai.deltachat.connector.model;

import lombok.Data;

/**
 * Сообщение, получаемое из DC JSON-RPC ({@code MessageObject}).
 * <p>
 * Read-only — содержит все поля, которые возвращают методы
 * {@code getMessage}, {@code getFreshMsgs} и т.д.
 * Агент пишет через {@link InputMessage}.
 *
 * @see <a href="https://github.com/chatmail/core/blob/main/deltachat-jsonrpc/src/api/types/message.rs">Rust struct MessageObject</a>
 */
@Data
public class OutputMessage {

    /** Уникальный ID сообщения. */
    private int id;

    /** ID чата, к которому относится сообщение. */
    private int chatId;

    /** Имя чата (на момент получения сообщения). */
    private String chatName;

    /** Текстовое содержимое (null, если его нет). */
    private String text;

    /** Путь к прикреплённому файлу (blob-директория). */
    private String file;

    /** MIME-тип прикреплённого файла. */
    private String fileMime;

    /** Размер прикреплённого файла в байтах. */
    private Long fileBytes;

    /** Ширина изображения/видео. */
    private Integer fileWidth;

    /** Высота изображения/видео. */
    private Integer fileHeight;

    /** Длительность аудио/видео в секундах. */
    private Integer fileDuration;

    /** Оригинальное имя файла. */
    private String filename;

    /** Текст цитируемого сообщения. */
    private String quotedText;

    /** ID цитируемого сообщения. */
    private Integer quotedMessageId;

    /** Переопределённое имя отправителя (если установлено). */
    private String overrideSenderName;

    /** ID отправителя. */
    private int senderId;

    /** Отображаемое имя отправителя. */
    private String senderDisplayName;

    /** Аватар отправителя (blob-путь). */
    private String senderAvatarFile;

    /** Статус сообщения (одна из {@link MessageState}). */
    private int state;

    /** Unix-таймстамп сортировки. */
    private long timestampSort;

    /** Unix-таймстамп сообщения. */
    private long timestamp;

    /** Тип вью-модели (одно из значений {@link MessageViewtype}). */
    private int viewType;

    /** Показывать ли замочек (protected/encrypted). */
    private boolean showpadlock;

    /** Emoji-реакция (JSON-строка). */
    private String emoji;

    /** Реакции (JSON-строка). */
    private String reactions;

    /** Тема сообщения (для групп). */
    private String subject;

    /** Таймер самоуничтожения (секунды). */
    private int ephemeralTimer;

    /** Unixtimestamp самоуничтожения. */
    private Long ephemeralTimestamp;
}
