package ru.daniil4jk.ai.deltachat.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FullChat {
    private int id;
    private String name;
    @JsonProperty("isEncrypted")
    private boolean isEncrypted;
    private String profileImage;
    private boolean archived;
    private boolean pinned;
    private String chatType;
    @JsonProperty("isUnpromoted")
    private boolean isUnpromoted;
    @JsonProperty("isSelfTalk")
    private boolean isSelfTalk;
    private List<Integer> contactIds;
    private List<Integer> pastContactIds;
    private String color;
    private int freshMessageCounter;
    @JsonProperty("isContactRequest")
    private boolean isContactRequest;
    @JsonProperty("isDeviceChat")
    private boolean isDeviceChat;
    private boolean selfInGroup;
    @JsonProperty("isMuted")
    private boolean isMuted;
    private int ephemeralTimer;
    private boolean canSend;
    private boolean wasSeenRecently;
    private String mailingListAddress;
}
