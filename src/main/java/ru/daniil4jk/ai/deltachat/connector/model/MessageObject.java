package ru.daniil4jk.ai.deltachat.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageObject {
    private int id;
    private int chatId;
    private int fromId;
    private String text;
    private String viewType;
    private int state;
    private long timestamp;
    private long sortTimestamp;
    private long receivedTimestamp;
    private boolean hasDeviatingTimestamp;
    private String subject;
    private boolean showPadlock;
    private boolean isInfo;
    private boolean isForwarded;
    private boolean isBot;
    private String systemMessageType;
    private Integer infoContactId;
    private int duration;
    private int dimensionsHeight;
    private int dimensionsWidth;
    private String overrideSenderName;
    private ContactObject sender;
    private String file;
    private String fileMime;
    private long fileBytes;
    private String fileName;
    private String downloadState;
    private Integer originalMsgId;
    private Integer savedMessageId;
    private boolean isEdited;
    private boolean hasLocation;
    private boolean hasHtml;
    private String error;
}
