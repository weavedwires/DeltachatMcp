package ru.daniil4jk.ai.deltachat.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactObject {
    private int id;
    private String address;
    private String displayName;
    private String name;
    private String nameAndAddr;
    private String authName;
    private String status;
    private String color;
    private String profileImage;
    private boolean isBlocked;
    private boolean isKeyContact;
    private boolean e2eeAvail;
    private boolean isVerified;
    private Integer verifierId;
    private long lastSeen;
    private boolean wasSeenRecently;
    private boolean isBot;
}
