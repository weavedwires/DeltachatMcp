package ru.daniil4jk.ai.deltachat.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageData {
    private String text;
    private String html;
    private MessageViewtype viewtype;
    private String file;
    private String filename;
    private double[] location;
    private String overrideSenderName;
    private Integer quotedMessageId;
    private String quotedText;
}
