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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeltachatServiceImpl implements DeltachatService {

    private static final Logger log = LoggerFactory.getLogger(DeltachatServiceImpl.class);

    /** dc_msg_state: 10 = InFresh (не прочитано и не показано), 13 = InNoticed (показано, но не открыто). */
    private static final int STATE_IN_FRESH = 10;
    private static final int STATE_IN_NOTICED = 13;
    /** Сколько сообщений подтягивать за один батч при сканировании непрочитанных. */
    private static final int UNREAD_SCAN_CHUNK = 200;

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
                result.add(fullChat(idNode.asInt()));
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

    /**
     * Непрочитанные сообщения чата.
     * <p>
     * Раньше здесь использовался {@code get_fresh_msgs} — но это API для уведомлений и бейджей:
     * ядро (chatmail/core, src/context.rs) отдаёт только сообщения не заблокированных и
     * <b>не заглушённых</b> чатов ({@code AND NOT(c.muted_until=-1 OR c.muted_until>now)}), тогда как
     * счётчик {@code freshMessageCounter} (src/chat.rs) считает все сообщения в состоянии
     * InFresh в чате, независимо от mute. Отсюда и был разрыв: в заглушённом чате счётчик > 0,
     * а {@code get_fresh_msgs} возвращает пустой список → «No messages».
     * <p>
     * Теперь непрочитанные определяются по самому сообщению ({@code state} = InFresh/InNoticed),
     * поэтому режим «не беспокоить» больше не скрывает переписку.
     *
     * @return до {@code n} сообщений, от старых к новым
     */
    @Override
    public List<MessageObject> getUnreadMessages(int chatId, int n, boolean doMarkAsRead) {
        checkAccount();
        FullChat chat = fullChat(chatId);
        int unreadCount = chat.getFreshMessageCounter();
        if (unreadCount <= 0) {
            return List.of();
        }
        List<Integer> allIds = messageIds(chatId);
        if (allIds.isEmpty()) {
            return List.of();
        }

        int limit = Math.max(1, n);
        int window = Math.max(unreadCount, limit);
        List<MessageObject> collected = new ArrayList<>(); // от старых к новым
        Set<Integer> seen = new HashSet<>();
        int scanned = 0;
        int end = allIds.size();

        while (end > 0 && collected.size() < limit && scanned < window) {
            int start = Math.max(0, end - UNREAD_SCAN_CHUNK);
            List<Integer> chunk = allIds.subList(start, end);
            List<MessageObject> chunkUnread = new ArrayList<>();
            for (MessageObject msg : messages(chunk)) {
                if (isUnread(msg) && seen.add(msg.getId())) {
                    chunkUnread.add(msg);
                }
            }
            collected.addAll(0, chunkUnread);
            scanned += chunk.size();
            end = start;
        }

        if (collected.isEmpty()) {
            log.debug("Chat {} reports {} unread but no message has an unread state", chatId, unreadCount);
            return List.of();
        }
        List<MessageObject> result = collected.size() > limit
                ? new ArrayList<>(collected.subList(0, limit))
                : collected;

        if (doMarkAsRead) {
            rpc.call("markseen_msgs", accountId, result.stream().map(MessageObject::getId).toList());
        }
        return result;
    }

    private boolean isUnread(MessageObject msg) {
        return msg.getState() == STATE_IN_FRESH || msg.getState() == STATE_IN_NOTICED;
    }

    @Override
    public List<MessageObject> getLastMessages(int chatId, int n) {
        checkAccount();
        FullChat chat = fullChat(chatId);
        if (chat.getFreshMessageCounter() > 0) {
            throw new UnreadMessagesExistException(chatId, chat.getFreshMessageCounter());
        }
        List<Integer> ids = messageIds(chatId);
        if (ids.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, ids.size() - n);
        List<Integer> lastIds = ids.subList(from, ids.size());
        List<MessageObject> result = messages(lastIds);
        rpc.call("markseen_msgs", accountId, lastIds);
        return result;
    }

    @Override
    public List<MessageObject> getMessagesBatch(int chatId, int n, int offset) {
        checkAccount();
        FullChat chat = fullChat(chatId);
        if (chat.getFreshMessageCounter() > 0) {
            throw new UnreadMessagesExistException(chatId, chat.getFreshMessageCounter());
        }
        List<Integer> ids = messageIds(chatId);
        if (ids.isEmpty()) {
            return List.of();
        }
        int end = ids.size() - offset;
        if (end <= 0) {
            return List.of();
        }
        int from = Math.max(0, end - n);
        List<Integer> batchIds = ids.subList(from, end);
        List<MessageObject> result = messages(batchIds);
        rpc.call("markseen_msgs", accountId, batchIds);
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

    // ---- helpers ----

    private FullChat fullChat(int chatId) {
        JsonNode node = rpc.call("get_full_chat_by_id", accountId, chatId);
        return mapper.convertValue(node, FullChat.class);
    }

    /** Все id сообщений чата, от старых к новым. */
    private List<Integer> messageIds(int chatId) {
        JsonNode ids = rpc.call("get_message_ids", accountId, chatId, false, false);
        List<Integer> result = new ArrayList<>();
        if (ids != null && ids.isArray()) {
            for (JsonNode idNode : ids) {
                result.add(idNode.asInt());
            }
        }
        return result;
    }

    /** Батч-получение сообщений в том же порядке, в котором переданы id. */
    private List<MessageObject> messages(List<Integer> ids) {
        if (ids.isEmpty()) return List.of();
        JsonNode msgsMap = rpc.call("get_messages", accountId, List.copyOf(ids));
        List<MessageObject> result = new ArrayList<>();
        if (msgsMap == null || !msgsMap.isObject()) {
            return result;
        }
        for (Integer id : ids) {
            JsonNode loadResult = msgsMap.get(String.valueOf(id));
            if (loadResult == null) continue;
            // MessageLoadResult — internally tagged enum: {"kind":"message", ...} | {"kind":"loadingError", ...}
            if (loadResult.hasNonNull("kind") && "message".equals(loadResult.get("kind").asText())) {
                result.add(mapper.convertValue(loadResult, MessageObject.class));
            } else {
                log.debug("Message {} not loaded: {}", id, loadResult);
            }
        }
        return result;
    }

    private void checkAccount() {
        if (accountId == null) {
            throw new IllegalStateException(
                    "No account configured. Call import_from_backup first.");
        }
    }
}
