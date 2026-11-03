package ru.daniil4jk.ai.deltachat.connector.jsonrpc;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Типизированный контракт deltachat-rpc-server для LSP4J.
 * <p>
 * Имена методов должны совпадать с именами JSON-RPC методов сервера, а типы —
 * быть понятными Gson (Jackson-типы вроде {@code JsonNode} тут не годятся:
 * транспорт LSP4J сериализует Gson'ом). Тяжёлые DTO конвертируются в
 * {@code DeltachatRpcClient} уже на стороне Jackson.
 * <p>
 * Интерфейс нужен не для прокси-вызовов (см. {@code call(...)}), а чтобы
 * {@link org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler} знал типы результатов:
 * без этого LSP4J отбрасывает {@code result} и возвращает {@code null}.
 */
public interface DeltaChatApi {

    @JsonRequest(value = "get_system_info", useSegment = false)
    CompletableFuture<Map<String, Object>> getSystemInfo();

    @JsonRequest(value = "get_selected_account_id", useSegment = false)
    CompletableFuture<Integer> getSelectedAccountId();

    @JsonRequest(value = "get_all_account_ids", useSegment = false)
    CompletableFuture<List<Integer>> getAllAccountIds();

    @JsonRequest(value = "add_account", useSegment = false)
    CompletableFuture<Integer> addAccount();

    @JsonRequest(value = "import_backup", useSegment = false)
    CompletableFuture<Void> importBackup(int accountId, String backupPath, String passphrase);

    @JsonRequest(value = "start_io", useSegment = false)
    CompletableFuture<Void> startIo(int accountId);

    @JsonRequest(value = "get_chatlist_entries", useSegment = false)
    CompletableFuture<List<Integer>> getChatlistEntries(int accountId, Integer listFlags, String query,
                                                        Integer queryContactId);

    @JsonRequest(value = "get_full_chat_by_id", useSegment = false)
    CompletableFuture<Map<String, Object>> getFullChatById(int accountId, int chatId);

    @JsonRequest(value = "get_fresh_msg_cnt", useSegment = false)
    CompletableFuture<Integer> getFreshMsgCnt(int accountId, int chatId);

    @JsonRequest(value = "get_message_ids", useSegment = false)
    CompletableFuture<List<Integer>> getMessageIds(int accountId, int chatId, boolean infoOnly,
                                                   boolean addDaymarker);

    @JsonRequest(value = "get_messages", useSegment = false)
    CompletableFuture<Map<String, Object>> getMessages(int accountId, List<Integer> messageIds);

    @JsonRequest(value = "get_message", useSegment = false)
    CompletableFuture<Map<String, Object>> getMessage(int accountId, int msgId);

    @JsonRequest(value = "markseen_msgs", useSegment = false)
    CompletableFuture<Void> markseenMsgs(int accountId, List<Integer> msgIds);

    @JsonRequest(value = "send_msg", useSegment = false)
    CompletableFuture<Integer> sendMsg(int accountId, int chatId, Map<String, Object> data);
}
