package ru.daniil4jk.ai.deltachat.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FullChat {
    private int id;
    private String name;
    private boolean isEncrypted;
    private String profileImage;
    private boolean archived;
    private boolean pinned;
    private String chatType;
    private boolean isUnpromoted;
    private boolean isSelfTalk;
    private List<Integer> contactIds;
    private List<Integer> pastContactIds;
    private String color;
    private int freshMessageCounter;
    private boolean isContactRequest;
    private boolean isDeviceChat;
    private boolean selfInGroup;
    private boolean isMuted;
    private int ephemeralTimer;
    private boolean canSend;
    private boolean wasSeenRecently;
    private String mailingListAddress;
}
