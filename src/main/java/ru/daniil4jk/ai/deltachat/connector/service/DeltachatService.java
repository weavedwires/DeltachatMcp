package ru.daniil4jk.ai.deltachat.connector.service;

import ru.daniil4jk.ai.deltachat.connector.model.FullChat;
import ru.daniil4jk.ai.deltachat.connector.model.MessageData;
import ru.daniil4jk.ai.deltachat.connector.model.MessageObject;

import java.util.List;

public interface DeltachatService {

    int importFromBackup(String backupPath, String passphrase);

    List<FullChat> listChats(int listFlags, String query);

    List<FullChat> listUnreadChats();

    List<MessageObject> getUnreadMessages(int chatId, int n, boolean doMarkAsRead);

    List<MessageObject> getLastMessages(int chatId, int n);

    MessageObject getMessage(int msgId);

    int sendMessage(int chatId, MessageData msg);
}
