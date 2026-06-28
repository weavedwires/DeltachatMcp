package ru.daniil4jk.ai.deltachat.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.daniil4jk.ai.deltachat.connector.jsonrpc.DeltachatRpcClient;
import ru.daniil4jk.ai.deltachat.connector.model.FullChat;
import ru.daniil4jk.ai.deltachat.connector.model.MessageData;
import ru.daniil4jk.ai.deltachat.connector.model.MessageObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class DeltachatServiceImpl implements DeltachatService {

    private static final Logger log = LoggerFactory.getLogger(DeltachatServiceImpl.class);

    private final DeltachatRpcClient rpc;
    private final ObjectMapper mapper;
    private Integer accountId;

    public DeltachatServiceImpl(DeltachatRpcClient rpc, ObjectMapper mapper) {
        this.rpc = rpc;
        this.mapper = mapper;
    }

    public void init() {
        // try selected account
        JsonNode selected = rpc.call("get_selected_account_id");
        if (selected != null && !selected.isNull()) {
            accountId = selected.asInt();
            log.info("Using selected account id={}", accountId);
            rpc.call("start_io", accountId);
            log.info("Started IO for account id={}", accountId);
            return;
        }
        // try first available account
        JsonNode all = rpc.call("get_all_account_ids");
        if (all != null && all.isArray() && all.size() > 0) {
            accountId = all.get(0).asInt();
            log.info("Using first account id={}", accountId);
            rpc.call("start_io", accountId);
            log.info("Started IO for account id={}", accountId);
            return;
        }
        log.warn("No accounts found — call importFromBackup first");
    }

    public boolean hasAccount() {
        return accountId != null;
    }

    @Override
    public int importFromBackup(String backupPath, String passphrase) {
        int newId = rpc.call("add_account").asInt();
        String pp = passphrase != null && !passphrase.isEmpty() ? passphrase : "";
        rpc.call("import_backup", newId, backupPath, pp);
        rpc.call("start_io", newId);
        accountId = newId;
        log.info("Imported backup into account id={}", newId);
        return newId;
    }

    @Override
    public List<FullChat> listChats(int listFlags, String query) {
        checkAccount();
        JsonNode ids = rpc.call("get_chatlist_entries", accountId, listFlags, query, null);
        List<FullChat> result = new ArrayList<>();
        if (ids != null && ids.isArray()) {
            for (JsonNode idNode : ids) {
                int chatId = idNode.asInt();
                JsonNode chat = rpc.call("get_full_chat_by_id", accountId, chatId);
                result.add(mapper.convertValue(chat, FullChat.class));
            }
        }
        return result;
    }

    @Override
    public List<FullChat> listUnreadChats() {
        return listChats(0, null).stream()
                .filter(c -> c.getFreshMessageCounter() > 0)
                .sorted(Comparator.comparing(FullChat::getFreshMessageCounter))
                .toList();
    }

    @Override
    public List<MessageObject> getUnreadMessages(int chatId, int n) {
        checkAccount();
        // get all fresh message IDs across all chats
        JsonNode freshIds = rpc.call("get_fresh_msgs", accountId);
        if (freshIds == null || !freshIds.isArray() || freshIds.isEmpty()) {
            return List.of();
        }
        // fetch all fresh messages in one batch
        List<Integer> allFreshIds = new ArrayList<>();
        for (JsonNode idNode : freshIds) {
            allFreshIds.add(idNode.asInt());
        }
        JsonNode msgsMap = rpc.call("get_messages", accountId, allFreshIds);

        // filter by chatId, collect matching IDs and messages
        List<Integer> matchedIds = new ArrayList<>();
        List<MessageObject> matched = new ArrayList<>();
        if (msgsMap != null && msgsMap.isObject()) {
            var iter = msgsMap.fields();
            while (iter.hasNext()) {
                var entry = iter.next();
                JsonNode loadResult = entry.getValue();
                // MessageLoadResult is an internally-tagged enum with inlined fields:
                // {"kind": "message", "id": ..., "chatId": ..., ...}
                // — no "message" wrapper, fields are at the top level of loadResult
                if (loadResult.has("kind") && "message".equals(loadResult.get("kind").asText())
                        && loadResult.has("chatId")
                        && loadResult.get("chatId").asInt() == chatId) {
                    matched.add(mapper.convertValue(loadResult, MessageObject.class));
                    matchedIds.add(Integer.parseInt(entry.getKey()));
                }
            }
        }
        if (matched.isEmpty()) {
            return List.of();
        }
        // take first n and mark them seen
        int takeCount = Math.min(n, matched.size());
        List<Integer> takeIds = matchedIds.subList(0, takeCount);
        List<MessageObject> result = matched.subList(0, takeCount);
        rpc.call("markseen_msgs", accountId, takeIds);
        return result;
    }

    @Override
    public List<MessageObject> getLastMessages(int chatId, int n) {
        checkAccount();
        // check for unread
        JsonNode chatNode = rpc.call("get_full_chat_by_id", accountId, chatId);
        FullChat chat = mapper.convertValue(chatNode, FullChat.class);
        if (chat.getFreshMessageCounter() > 0) {
            throw new UnreadMessagesExistException(chatId, chat.getFreshMessageCounter());
        }
        // get all message IDs for the chat
        JsonNode ids = rpc.call("get_message_ids", accountId, chatId, false, false);
        if (ids == null || !ids.isArray() || ids.isEmpty()) {
            return List.of();
        }
        int size = ids.size();
        int from = Math.max(0, size - n);
        List<Integer> lastIds = new ArrayList<>();
        for (int i = from; i < size; i++) {
            lastIds.add(ids.get(i).asInt());
        }
        // batch fetch
        JsonNode msgsMap = rpc.call("get_messages", accountId, lastIds);
        rpc.call("markseen_msgs", accountId, lastIds);

        List<MessageObject> result = new ArrayList<>();
        if (msgsMap != null && msgsMap.isObject()) {
            var iter = msgsMap.fields();
            while (iter.hasNext()) {
                var entry = iter.next();
                JsonNode loadResult = entry.getValue();
                // MessageLoadResult internally tagged: {"kind": "message", ...}
                if (loadResult.has("kind") && "message".equals(loadResult.get("kind").asText())) {
                    result.add(mapper.convertValue(loadResult, MessageObject.class));
                }
            }
        }
        return result;
    }

    @Override
    public MessageObject getMessage(int msgId) {
        checkAccount();
        JsonNode msg = rpc.call("get_message", accountId, msgId);
        return mapper.convertValue(msg, MessageObject.class);
    }

    @Override
    public int sendMessage(int chatId, MessageData msg) {
        checkAccount();
        JsonNode result = rpc.call("send_msg", accountId, chatId, msg);
        return result.asInt();
    }

    private void checkAccount() {
        if (accountId == null) {
            throw new IllegalStateException(
                    "No account configured. Call import_from_backup first.");
        }
    }
}
