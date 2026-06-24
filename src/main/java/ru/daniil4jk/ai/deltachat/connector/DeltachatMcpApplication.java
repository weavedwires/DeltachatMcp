package ru.daniil4jk.ai.deltachat.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.daniil4jk.ai.deltachat.connector.jsonrpc.DeltachatRpcClient;
import ru.daniil4jk.ai.deltachat.connector.mcp.DeltachatMcpServer;
import ru.daniil4jk.ai.deltachat.connector.service.DeltachatServiceImpl;

import java.util.concurrent.CountDownLatch;

public class DeltachatMcpApplication {

    private static final Logger log = LoggerFactory.getLogger(DeltachatMcpApplication.class);

    public static void main(String[] args) throws Exception {
        var objectMapper = new ObjectMapper();

        var rpcClient = new DeltachatRpcClient(objectMapper);
        rpcClient.start();

        var service = new DeltachatServiceImpl(rpcClient, objectMapper);
        service.init();

        log.info("Starting MCP server (stdio transport)");
        var server = new DeltachatMcpServer(service, objectMapper);
        server.create().build();

        new CountDownLatch(1).await();
    }
}
