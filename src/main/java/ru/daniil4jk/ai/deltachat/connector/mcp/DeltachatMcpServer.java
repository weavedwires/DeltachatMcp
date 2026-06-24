package ru.daniil4jk.ai.deltachat.connector.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import ru.daniil4jk.ai.deltachat.connector.model.Chat;
import ru.daniil4jk.ai.deltachat.connector.model.InputMessage;
import ru.daniil4jk.ai.deltachat.connector.model.OutputMessage;

import ru.daniil4jk.ai.deltachat.connector.service.DeltachatService;
import ru.daniil4jk.ai.deltachat.connector.service.UnreadMessagesExistException;

import java.util.List;
import java.util.Map;


/**
 * MCP-сервер, который экспортирует инструменты для работы с DeltaChat.
 * <p>
 * Запускается как подпроцесс через {@link StdioServerTransportProvider} —
 * общается с MCP-клиентом (агентом) через stdin/stdout.
 */
public class DeltachatMcpServer {

    // ───── локальные рекорды для десериализации аргументов инструментов ─────

    private record ListChatsArgs(int listFlags, String query) {}
    private record GetMessagesArgs(int n) {}
    private record GetLastMessagesArgs(int chatId, int n) {}
    private record GetMessageArgs(int msgId) {}
    private record SendMessageArgs(int chatId, InputMessage message) {}

    // ───── зависимости ─────

    private final DeltachatService deltachatService;
    private final ObjectMapper objectMapper;

    public DeltachatMcpServer(DeltachatService deltachatService, ObjectMapper objectMapper) {
        this.deltachatService = deltachatService;
        this.objectMapper = objectMapper;
    }

    /**
     * Собрать и запустить MCP-сервер.
     */
    public McpServer.SyncSpecification create() {
        var transport = new StdioServerTransportProvider(objectMapper);

        return McpServer.sync(transport)
                .serverInfo("deltachat-mcp", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(
                        toolListChats(),
                        toolListUnreadChats(),
                        toolGetUnreadMessages(),
                        toolGetLastMessages(),
                        toolGetMessage(),
                        toolSendMessage()
                );
    }

    // ───── инструменты ─────

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
                    List<Chat> chats = deltachatService.listChats(p.listFlags(), p.query());
                    return new McpSchema.CallToolResult(List.of(
                            new McpSchema.TextContent(json(chats))
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
                    List<Chat> chats = deltachatService.listUnreadChats();
                    return new McpSchema.CallToolResult(List.of(
                            new McpSchema.TextContent(json(chats))
                    ), false);
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification toolGetUnreadMessages() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "get_unread_messages",
                        "Get first N unread messages (from cursor) and mark them as read",
                        new McpSchema.JsonSchema(
                                "object",
                                Map.of(
                                        "n", Map.of(
                                                "type", "integer",
                                                "description", "Maximum number of messages to return",
                                                "minimum", 1
                                        )
                                ),
                                List.of("n"),
                                false
                        )
                ),
                (exchange, args) -> {
                    var p = objectMapper.convertValue(args, GetMessagesArgs.class);
                    List<OutputMessage> msgs = deltachatService.getUnreadMessages(p.n());
                    return new McpSchema.CallToolResult(List.of(
                            new McpSchema.TextContent(json(msgs))
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
                        List<OutputMessage> msgs = deltachatService.getLastMessages(p.chatId(), p.n());
                        return new McpSchema.CallToolResult(List.of(
                                new McpSchema.TextContent(json(msgs))
                        ), false);
                    } catch (UnreadMessagesExistException e) {
                        return new McpSchema.CallToolResult(List.of(
                                new McpSchema.TextContent(
                                        "Error: " + e.getMessage() +
                                                ". Call get_unread_messages first."
                                )
                        ), true); // isError=true
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
                    OutputMessage msg = deltachatService.getMessage(p.msgId());
                    return new McpSchema.CallToolResult(List.of(
                            new McpSchema.TextContent(json(msg))
                    ), false);
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification toolSendMessage() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "send_message",
                        "Send a message to a chat. Accepts an InputMessage (write-only) — " +
                                "send only the fields you want to set (text, file, html, " +
                                "quotedMessageId, overrideSenderName, location, etc.).",
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
                                                        "file", Map.of("type", "string",
                                                                "description", "Path to file in blob directory"),
                                                        "quotedMessageId", Map.of("type", "integer",
                                                                "description", "ID of the message to quote"),
                                                        "overrideSenderName", Map.of("type", "string",
                                                                "description", "Override sender display name"),
                                                        "location", Map.of(
                                                                "type", "object",
                                                                "description", "Latitude/longitude",
                                                                "properties", Map.of(
                                                                        "latitude", Map.of("type", "number"),
                                                                        "longitude", Map.of("type", "number")
                                                                )
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

    // ───── утилиты ─────

    private String json(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
