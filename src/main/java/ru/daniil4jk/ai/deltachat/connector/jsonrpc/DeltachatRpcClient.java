package ru.daniil4jk.ai.deltachat.connector.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Низкоуровневый JSON-RPC 2.0 клиент для deltachat-rpc-server.
 * <p>
 * Запускает подпроцесс deltachat-rpc-server, пишет запросы в stdin,
 * читает ответы и события из stdout.
 * <p>
 * TODO: имплементация — обработка событий, пул потоков, реконнект.
 */
public class DeltachatRpcClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(DeltachatRpcClient.class);

    private final ObjectMapper objectMapper;
    private final String rpcServerPath;

    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;
    private final AtomicInteger requestId = new AtomicInteger(1);

    // TODO: очередь событий, маппинг requestId -> CompletableFuture

    public DeltachatRpcClient(ObjectMapper objectMapper) {
        this(objectMapper, "deltachat-rpc-server");
    }

    public DeltachatRpcClient(ObjectMapper objectMapper, String rpcServerPath) {
        this.objectMapper = objectMapper;
        this.rpcServerPath = rpcServerPath;
    }

    /**
     * Запустить подпроцесс deltachat-rpc-server.
     */
    public void start() throws IOException {
        log.info("Starting deltachat-rpc-server: {}", rpcServerPath);
        var pb = new ProcessBuilder(rpcServerPath);
        pb.redirectErrorStream(true);
        process = pb.start();

        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        // TODO: запустить тред-читатель для событий и ответов
    }

    /**
     * Послать JSON-RPC запрос и дождаться ответа.
     *
     * @param method имя метода (в camelCase, например "getMessage")
     * @param params позиционные параметры
     * @return JSON-узел с результатом (поле "result" ответа)
     */
    public JsonNode call(String method, Object... params) throws IOException {
        int id = requestId.getAndIncrement();
        var request = Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", method,
                "params", params
        );

        String json = objectMapper.writeValueAsString(request);
        log.debug("-> {}", json);

        writer.write(json);
        writer.newLine();
        writer.flush();

        // TODO: читать из очереди, а не напрямую (из-за событий)
        String responseLine = reader.readLine();
        if (responseLine == null) {
            throw new IOException("deltachat-rpc-server closed stdin");
        }
        log.debug("<- {}", responseLine);

        JsonNode response = objectMapper.readTree(responseLine);
        JsonNode error = response.get("error");
        if (error != null) {
            throw new IOException("JSON-RPC error: " + error);
        }

        return response.get("result");
    }

    @Override
    public void close() {
        if (process != null) {
            process.destroy();
        }
    }
}
