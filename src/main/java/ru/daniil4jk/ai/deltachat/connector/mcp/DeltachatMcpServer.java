package ru.daniil4jk.ai.deltachat.connector.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import ru.daniil4jk.ai.deltachat.connector.model.FullChat;
import ru.daniil4jk.ai.deltachat.connector.model.MessageData;
import ru.daniil4jk.ai.deltachat.connector.model.MessageObject;
import ru.daniil4jk.ai.deltachat.connector.service.DeltachatService;
import ru.daniil4jk.ai.deltachat.connector.service.UnreadMessagesExistException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DeltachatMcpServer {

    private record ListChatsArgs(int listFlags, String query) {}
    private record GetMessagesArgs(int chatId, int n, boolean doMarkAsRead) {}
    private record GetLastMessagesArgs(int chatId, int n) {}
    private record GetMessagesBatchArgs(int chatId, int n, int offset) {}
    private record GetMessageArgs(int msgId) {}
    private record SendMessageArgs(int chatId, MessageData message) {}
    private record ImportBackupArgs(String backupPath, String passphrase) {}

    /** Таймаут обработки tool-вызова на стороне MCP; должен быть меньше клиентского. */
    private static final Duration TOOL_TIMEOUT = Duration.ofSeconds(240);

    private final DeltachatService deltachatService;
    private final ObjectMapper objectMapper;
    private final McpJsonMapper jsonMapper;

    public DeltachatMcpServer(DeltachatService deltachatService, ObjectMapper objectMapper) {
        this.deltachatService = deltachatService;
        this.objectMapper = objectMapper;
        this.jsonMapper = new JacksonMcpJsonMapper(objectMapper);
    }

    public McpSyncServer create() {
        var transport = new StdioServerTransportProvider(jsonMapper);

        return McpServer.sync(transport)
                .serverInfo("deltachat-mcp", "2.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(false)
                        .build())
                .requestTimeout(TOOL_TIMEOUT)
                .tools(
                        toolImportFromBackup(),
                        toolListChats(),
                        toolListUnreadChats(),
                        toolGetUnreadMessages(),
                        toolGetLastMessages(),
                        toolGetMessagesBatch(),
                        toolGetMessage(),
                        toolSendMessage()
                )
                .build();
    }

    // ---- tools ----

    private McpServerFeatures.SyncToolSpecification toolImportFromBackup() {
        return tool("import_from_backup",
                "Import Delta Chat account from a .tar backup archive. " +
                        "Must be called first if no accounts are configured.",
                schema(Map.of(
                        "backupPath", Map.of(
                                "type", "string",
                                "description", "Full path to the .tar backup archive"
                        ),
                        "passphrase", Map.of(
                                "type", "string",
                                "description", "Optional backup passphrase"
                        )
                ), List.of("backupPath")),
                args -> {
                    var p = convert(args, ImportBackupArgs.class);
                    int accountId = deltachatService.importFromBackup(p.backupPath(), p.passphrase());
                    return "Account imported, id=" + accountId + ". Tools are now available.";
                });
    }

    private McpServerFeatures.SyncToolSpecification toolListChats() {
        return tool("list_chats",
                "List all chats with optional filter and search query",
                schema(Map.of(
                        "listFlags", Map.of(
                                "type", "integer",
                                "description", "Filter flags: 0=all, 9=archived, 512=no groups",
                                "default", 0
                        ),
                        "query", Map.of(
                                "type", "string",
                                "description", "Optional search by name/email"
                        )
                ), List.of()),
                args -> {
                    var p = convert(args, ListChatsArgs.class);
                    return formatChats(deltachatService.listChats(p.listFlags(), p.query()));
                });
    }

    private McpServerFeatures.SyncToolSpecification toolListUnreadChats() {
        return tool("list_unread_chats",
                "List all chats that have unread messages (muted chats included)",
                schema(Map.of(), List.of()),
                args -> formatUnreadChats(deltachatService.listUnreadChats()));
    }

    private McpServerFeatures.SyncToolSpecification toolGetUnreadMessages() {
        return tool("get_unread_messages",
                "Get up to N unread messages of a specific chat, oldest first. " +
                        "Works for muted chats too (unread state is taken from the messages themselves, " +
                        "not from the notification API). " +
                        "By default does NOT mark them read — the same messages are returned on " +
                        "every call. Set doMarkAsRead=true to mark only the returned messages as seen " +
                        "(destructive: a later call will no longer return them).",
                schema(Map.of(
                        "chatId", Map.of(
                                "type", "integer",
                                "description", "Chat ID"
                        ),
                        "n", Map.of(
                                "type", "integer",
                                "description", "Maximum number of messages to return",
                                "minimum", 1
                        ),
                        "doMarkAsRead", Map.of(
                                "type", "boolean",
                                "description", "If true, mark the returned messages as read/seen after fetching. " +
                                        "Default false — messages stay unread and can be re-read on a later call.",
                                "default", false
                        )
                ), List.of("chatId", "n")),
                args -> {
                    var p = convert(args, GetMessagesArgs.class);
                    return formatMessages(
                            deltachatService.getUnreadMessages(p.chatId(), p.n(), p.doMarkAsRead()));
                });
    }

    private McpServerFeatures.SyncToolSpecification toolGetLastMessages() {
        return tool("get_last_messages",
                "Get last N messages from a chat and mark them read. " +
                        "Throws error if there are unread messages — use get_unread_messages first.",
                schema(Map.of(
                        "chatId", Map.of("type", "integer", "description", "Chat ID"),
                        "n", Map.of("type", "integer", "description", "Number of messages to retrieve",
                                "minimum", 1)
                ), List.of("chatId", "n")),
                args -> {
                    var p = convert(args, GetLastMessagesArgs.class);
                    return formatMessages(deltachatService.getLastMessages(p.chatId(), p.n()));
                });
    }

    private McpServerFeatures.SyncToolSpecification toolGetMessagesBatch() {
        return tool("get_messages_batch",
                "Get a batch of messages from a chat, counted back from the last (most recent) " +
                        "message, not by id. offset=0 returns the last n messages, offset=n returns " +
                        "the n messages before those, etc. Marks returned messages read. " +
                        "Throws error if there are unread messages — use get_unread_messages first.",
                schema(Map.of(
                        "chatId", Map.of("type", "integer", "description", "Chat ID"),
                        "n", Map.of("type", "integer", "description", "Number of messages to retrieve",
                                "minimum", 1),
                        "offset", Map.of(
                                "type", "integer",
                                "description", "How many messages to skip from the end of the chat. " +
                                        "0 = newest messages, n = previous n messages, etc.",
                                "minimum", 0,
                                "default", 0
                        )
                ), List.of("chatId", "n")),
                args -> {
                    var p = convert(args, GetMessagesBatchArgs.class);
                    return formatMessages(deltachatService.getMessagesBatch(p.chatId(), p.n(), p.offset()));
                });
    }

    private McpServerFeatures.SyncToolSpecification toolGetMessage() {
        return tool("get_message",
                "Get a message by its ID",
                schema(Map.of(
                        "msgId", Map.of("type", "integer", "description", "Message ID")
                ), List.of("msgId")),
                args -> {
                    var p = convert(args, GetMessageArgs.class);
                    MessageObject msg = deltachatService.getMessage(p.msgId());
                    return formatMessages(List.of(msg));
                });
    }

    private McpServerFeatures.SyncToolSpecification toolSendMessage() {
        return tool("send_message",
                "Send a message to a chat. Accepts a MessageData object — " +
                        "send only the fields you want to set (text, file, html, " +
                        "viewtype, quotedMessageId, overrideSenderName, location, etc.).",
                schema(Map.of(
                        "chatId", Map.of("type", "integer", "description", "Target chat ID"),
                        "message", Map.of(
                                "type", "object",
                                "description", "Message fields to set — all optional, null/absent fields are skipped",
                                "properties", Map.of(
                                        "text", Map.of("type", "string", "description", "Message text"),
                                        "html", Map.of("type", "string", "description", "HTML version of text"),
                                        "viewtype", Map.of("type", "string",
                                                "description", "View type: Text, Image, Gif, Audio, Voice, Video, File, etc."),
                                        "file", Map.of("type", "string", "description", "Path to file in blob directory"),
                                        "filename", Map.of("type", "string", "description", "Original filename"),
                                        "quotedMessageId", Map.of("type", "integer",
                                                "description", "ID of the message to quote"),
                                        "overrideSenderName", Map.of("type", "string",
                                                "description", "Override sender display name"),
                                        "location", Map.of(
                                                "type", "array",
                                                "description", "Latitude/longitude as [lat, lng]",
                                                "items", Map.of("type", "number")
                                        )
                                )
                        )
                ), List.of("chatId", "message")),
                args -> {
                    var p = convert(args, SendMessageArgs.class);
                    int msgId = deltachatService.sendMessage(p.chatId(), p.message());
                    return "Sent, message ID: " + msgId;
                });
    }

    // ---- helpers ----

    private Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    private <T> T convert(Map<String, Object> args, Class<T> type) {
        return objectMapper.convertValue(args == null ? Map.of() : args, type);
    }

    /**
     * Общая обёртка tool-спеки: текст-обработчик, а любая ошибка превращается в
     * содержательный {@code isError}-ответ, а не в обрыв протокола.
     */
    private McpServerFeatures.SyncToolSpecification tool(String name,
                                                         String description,
                                                         Map<String, Object> inputSchema,
                                                         Function<Map<String, Object>, String> handler) {
        var tool = McpSchema.Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            try {
                return text(handler.apply(args), false);
            } catch (UnreadMessagesExistException e) {
                return text("Error: " + e.getMessage() + ". Call get_unread_messages first.", true);
            } catch (Exception e) {
                return text("Error: " + e.getClass().getSimpleName() + ": " + e.getMessage(), true);
            }
        });
    }

    private McpSchema.CallToolResult text(String text, boolean isError) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(text)
                .isError(isError)
                .build();
    }

    private String formatMessages(List<MessageObject> msgs) {
        if (msgs.isEmpty()) return "No messages.";
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return msgs.stream()
                .map(m -> {
                    String date = Instant.ofEpochSecond(m.getTimestamp())
                            .atZone(ZoneId.systemDefault())
                            .format(dtf);
                    String sender;
                    if (m.getOverrideSenderName() != null && !m.getOverrideSenderName().isEmpty()) {
                        sender = m.getOverrideSenderName();
                    } else if (m.getSender() != null) {
                        String dn = m.getSender().getDisplayName();
                        String na = m.getSender().getNameAndAddr();
                        sender = (dn != null && !dn.isEmpty()) ? dn : (na != null ? na : "Unknown");
                    } else {
                        sender = "Unknown";
                    }
                    String text = m.getText() != null ? m.getText().replace("\n", " ⏎ ") : "";
                    return sender + " " + date + ": " + text;
                })
                .collect(Collectors.joining("\n\n"));
    }

    private String formatChats(List<FullChat> chats) {
        if (chats.isEmpty()) return "No chats.";
        return chats.stream()
                .sorted(Comparator.comparingInt(FullChat::getId))
                .map(c -> "%s [id %d]%s%s%s".formatted(c.getName(), c.getId(),
                        c.isMuted() ? " (muted)" : "",
                        c.isContactRequest() ? " (contact request)" : "",
                        c.getFreshMessageCounter() > 0 ? " — unread " + c.getFreshMessageCounter() : ""))
                .collect(Collectors.joining("\n"));
    }

    private String formatUnreadChats(List<FullChat> chats) {
        if (chats.isEmpty()) return "No unread chats.";
        return chats.stream()
                .sorted(Comparator.comparingInt(FullChat::getFreshMessageCounter))
                .map(c -> "Чат \"%s\" с id %d имеет %d непрочитанных%s".formatted(
                        c.getName(), c.getId(), c.getFreshMessageCounter(),
                        c.isMuted() ? " (чат заглушён — на непрочитанные это не влияет)" : ""))
                .collect(Collectors.joining("\n"));
    }
}
