package ru.daniil4jk.ai.deltachat.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.daniil4jk.ai.deltachat.connector.mcp.DeltachatMcpServer;
import ru.daniil4jk.ai.deltachat.connector.service.DeltachatService;

/**
 * Точка входа в DeltachatMCP.
 * <p>
 * Запускает MCP-сервер на stdin/stdout транспорте.
 * MCP-клиент (агент) запускает этот JAR как подпроцесс.
 * <p>
 * TODO: собрать зависимости, имплементировать {@link DeltachatService},
 *       поднять {@code deltachat-rpc-server} через {@code DeltachatRpcClient}.
 */
public class DeltachatMcpApplication {

    public static void main(String[] args) throws Exception {
        var objectMapper = new ObjectMapper();

        // TODO: инициализировать DeltachatRpcClient, обернуть в DeltachatService
        DeltachatService deltachatService = null;

        var server = new DeltachatMcpServer(deltachatService, objectMapper);
        var mcpServer = server.create().build();

        // MCP сервер через StdioServerTransportProvider уже слушает stdin.
        // Просто ждём завершения.
        Thread.currentThread().join();
    }
}
