package ru.daniil4jk.ai.deltachat.connector.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import ru.daniil4jk.ai.deltachat.connector.model.FullChat;
import ru.daniil4jk.ai.deltachat.connector.model.MessageData;
import ru.daniil4jk.ai.deltachat.connector.model.MessageObject;

import ru.daniil4jk.ai.deltachat.connector.service.DeltachatService;
import ru.daniil4jk.ai.deltachat.connector.service.UnreadMessagesExistException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DeltachatMcpServer {

    private record ListChatsArgs(int listFlags, String query) {}
    private record GetMessagesArgs(int chatId, int n, boolean doMarkAsRead) {}
    private record GetLastMessagesArgs(int chatId, int n) {}
    private record GetMessageArgs(int msgId) {}
    private record SendMessageArgs(int chatId, MessageData message) {}
    private record ImportBackupArgs(String backupPath, String passphrase) {}

    private final DeltachatService deltachatService;
    private final ObjectMapper objectMapper;

    public DeltachatMcpServer(DeltachatService deltachatService, ObjectMapper objectMapper) {
        this.deltachatService = deltachatService;
        this.objectMapper = objectMapper;
    }

    public McpServer.SyncSpecification create() {
        var transport = new StdioServerTransportProvider(objectMapper);

        return McpServer.sync(transport)
                .serverInfo("deltachat-mcp", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(
                        toolImportFromBackup(),
                        toolListChats(),
                        toolListUnreadChats(),
                        toolGetUnreadMessages(),
                        toolGetLastMessages(),
                        toolGetMessage(),
                        toolSendMessage()
                );
    }

    private McpServerFeatures.SyncToolSpecification toolImportFromBackup() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "import_from_backup",
                        "Import Delta Chat account from a .tar backup archive. " +
                                "Must be called first if no accounts are configured.",
                        new McpSchema.JsonSchema(
                                "object",
                                Map.of(
                                        "backupPath", Map.of(
                                                "type", "string",
                                                "description", "Full path to the .tar backup archive"
                                        ),
                                        "passphrase", Map.of(
                                                "type", "string",
                                                "description", "Optional backup passphrase"
                                        )
                                ),
                                List.of("backupPath"),
                                false
                        )
                ),
                (exchange, args) -> {
                    var p = objectMapper.convertValue(args, ImportBackupArgs.class);
                    int accountId = deltachatService.importFromBackup(
                            p.backupPath(), p.passphrase());
                    return new McpSchema.CallToolResult(List.of(
                            new McpSchema.TextContent(
                                    "Account imported, id=" + accountId +
                                            ". Tools are now available.")
                    ), false);
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification toolListChats() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "list_chats",
                        "List all chats with optional filter and search query",
                        new McpSchema.JsonSchema(
                                "object",
                                Map.of(
                                        "listFlags", Map.of(
                                                "type", "integer",
                                                "description", "Filter flags: 0=all, 9=archived, 512=no groups",
                                                "default", 0
                                        ),
                                        "query", Map.of(
                                                "type", "string",
                                                "description", "Optional search by name/email"
                                        )
                                ),
                                List.of(),
                                false
                        )
                ),
                (exchange, args) -> {
                    var p = objectMapper.convertValue(args, ListChatsArgs.class);
                    List<FullChat> chats = deltachatService.listChats(p.listFlags(), p.query());
                    return new McpSchema.CallToolResult(List.of(
                            new McpSchema.TextContent(formatChats(chats))
                    ), false);
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification toolListUnreadChats() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "list_unread_chats",
                        "List all chats that have unread messages",
                        new McpSchema.JsonSchema("object", Map.of(), List.of(), false)
                ),
                (exchange, args) -> {
                    List<FullChat> chats = deltachatService.listUnreadChats();
                    return new McpSchema.CallToolResult(List.of(
                            new McpSchema.TextContent(formatUnreadChats(chats))
                    ), false);
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification toolGetUnreadMessages() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "get_unread_messages",
                        "Get up to N unread (fresh) messages from a specific chat. " +
                                "By default does NOT mark them read — the same messages are returned on " +
                                "every call. Set doMarkAsRead=true to mark only the returned messages as seen " +
                                "(destructive: a later call will no longer return them).",
                        new McpSchema.JsonSchema(
                                "object",
                                Map.of(
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
                                ),
                                List.of("chatId", "n"),
                                false
                        )
                ),
                (exchange, args) -> {
                    var p = objectMapper.convertValue(args, GetMessagesArgs.class);
                    List<MessageObject> msgs = deltachatService.getUnreadMessages(p.chatId(), p.n(), p.doMarkAsRead());
                    return new McpSchema.CallToolResult(List.of(
                            new McpSchema.TextContent(formatMessages(msgs))
                    ), false);
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification toolGetLastMessages() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "get_last_messages",
                        "Get last N messages from a chat and mark them read. " +
                                "Throws error if there are unread messages — use get_unread_messages first.",
                        new McpSchema.JsonSchema(
                                "object",
                                Map.of(
                                        "chatId", Map.of(
                                                "type", "integer",
                                                "description", "Chat ID"
                                        ),
                                        "n", Map.of(
                                                "type", "integer",
                                                "description", "Number of messages to retrieve",
                                                "minimum", 1
                                        )
                                ),
                                List.of("chatId", "n"),
                                false
                        )
                ),
                (exchange, args) -> {
                    var p = objectMapper.convertValue(args, GetLastMessagesArgs.class);
                    try {
                        List<MessageObject> msgs = deltachatService.getLastMessages(p.chatId(), p.n());
                        return new McpSchema.CallToolResult(List.of(
                                new McpSchema.TextContent(formatMessages(msgs))
                        ), false);
                    } catch (UnreadMessagesExistException e) {
                        return new McpSchema.CallToolResult(List.of(
                                new McpSchema.TextContent(
                                        "Error: " + e.getMessage() +
                                                ". Call get_unread_messages first."
                                )
                        ), true);
                    }
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification toolGetMessage() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "get_message",
                        "Get a message by its ID",
                        new McpSchema.JsonSchema(
                                "object",
                                Map.of(
                                        "msgId", Map.of(
                                                "type", "integer",
                                                "description", "Message ID"
                                        )
                                ),
                                List.of("msgId"),
                                false
                        )
                ),
                (exchange, args) -> {
                    var p = objectMapper.convertValue(args, GetMessageArgs.class);
                    MessageObject msg = deltachatService.getMessage(p.msgId());
                    return new McpSchema.CallToolResult(List.of(
                            new McpSchema.TextContent(formatMessages(List.of(msg)))
                    ), false);
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification toolSendMessage() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "send_message",
                        "Send a message to a chat. Accepts a MessageData object — " +
                                "send only the fields you want to set (text, file, html, " +
                                "viewtype, quotedMessageId, overrideSenderName, location, etc.).",
                        new McpSchema.JsonSchema(
                                "object",
                                Map.of(
                                        "chatId", Map.of(
                                                "type", "integer",
                                                "description", "Target chat ID"
                                        ),
                                        "message", Map.of(
                                                "type", "object",
                                                "description", "Message fields to set — all optional, null/absent fields are skipped",
                                                "properties", Map.of(
                                                        "text", Map.of("type", "string",
                                                                "description", "Message text"),
                                                        "html", Map.of("type", "string",
                                                                "description", "HTML version of text"),
                                                        "viewtype", Map.of("type", "string",
                                                                "description", "View type: Text, Image, Gif, Audio, Voice, Video, File, etc."),
                                                        "file", Map.of("type", "string",
                                                                "description", "Path to file in blob directory"),
                                                        "filename", Map.of("type", "string",
                                                                "description", "Original filename"),
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
                                ),
                                List.of("chatId", "message"),
                                false
                        )
                ),
                (exchange, args) -> {
                    var p = objectMapper.convertValue(args, SendMessageArgs.class);
                    int msgId = deltachatService.sendMessage(p.chatId(), p.message());
                    return new McpSchema.CallToolResult(List.of(
                            new McpSchema.TextContent("Sent, message ID: " + msgId)
                    ), false);
                }
        );
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
                .map(c -> "%s [id %d]".formatted(c.getName(), c.getId()))
                .collect(Collectors.joining("\n"));
    }

    private String formatUnreadChats(List<FullChat> chats) {
        if (chats.isEmpty()) return "No unread chats.";
        return chats.stream()
                .sorted(Comparator.comparingInt(FullChat::getFreshMessageCounter))
                .map(c -> "Чат \"%s\" с id %d имеет %d непрочитанных".formatted(
                        c.getName(), c.getId(), c.getFreshMessageCounter()))
                .collect(Collectors.joining("\n"));
    }
}
