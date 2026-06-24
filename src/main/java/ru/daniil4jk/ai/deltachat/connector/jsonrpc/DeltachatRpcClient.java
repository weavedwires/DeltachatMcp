package ru.daniil4jk.ai.deltachat.connector.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DeltachatRpcClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(DeltachatRpcClient.class);

    private final ObjectMapper objectMapper;
    private final String rpcServerPath;
    private final String[] rpcServerArgs;

    private Process process;
    private final AtomicInteger requestId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final BlockingQueue<String> writeQueue = new LinkedBlockingQueue<>();

    private Thread readerThread;
    private Thread writerThread;
    private volatile boolean closing;

    private String accountsPath;

    public DeltachatRpcClient(ObjectMapper objectMapper) {
        this(objectMapper, "deltachat-rpc-server");
    }

    public DeltachatRpcClient(ObjectMapper objectMapper, String rpcServerPath, String... extraArgs) {
        this.objectMapper = objectMapper;
        this.rpcServerPath = rpcServerPath;
        this.rpcServerArgs = extraArgs != null ? extraArgs : new String[0];
        String envPath = System.getenv("DC_ACCOUNTS_PATH");
        if (envPath == null || envPath.isBlank()) {
            envPath = System.getProperty("deltachat.accountsPath", "accounts");
        }
        this.accountsPath = envPath;
    }

    public void start() throws IOException {
        log.info("Starting deltachat-rpc-server: {} (accounts={})", rpcServerPath, accountsPath);
        var cmd = new String[1 + rpcServerArgs.length];
        cmd[0] = rpcServerPath;
        System.arraycopy(rpcServerArgs, 0, cmd, 1, rpcServerArgs.length);
        var pb = new ProcessBuilder(cmd);
        pb.environment().put("DC_ACCOUNTS_PATH", accountsPath);
        pb.redirectErrorStream(false);
        process = pb.start();

        readerThread = new Thread(this::readerLoop, "dc-rpc-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        writerThread = new Thread(this::writerLoop, "dc-rpc-writer");
        writerThread.setDaemon(true);
        writerThread.start();

        // health-check
        try {
            call("get_system_info");
        } catch (Exception e) {
            String stderr = readStderr();
            throw new IOException("RPC server failed to start" +
                    (stderr != null ? ": " + stderr : ""), e);
        }
        log.info("deltachat-rpc-server is ready");
    }

    public JsonNode call(String method, Object... params) {
        int id = requestId.getAndIncrement();
        var request = Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", method,
                "params", params
        );
        String json;
        try {
            json = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request", e);
        }
        log.debug("-> {}", json);

        var future = new CompletableFuture<JsonNode>();
        pending.put(id, future);
        writeQueue.add(json);

        try {
            JsonNode result = future.get(30, TimeUnit.SECONDS);
            return result;
        } catch (TimeoutException e) {
            pending.remove(id);
            throw new RuntimeException("RPC call timed out: " + method, e);
        } catch (Exception e) {
            pending.remove(id);
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("RPC call failed: " + method, e);
        }
    }

    private void readerLoop() {
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("<- {}", line);
                JsonNode response;
                try {
                    response = objectMapper.readTree(line);
                } catch (Exception e) {
                    log.warn("Failed to parse RPC response: {}", line, e);
                    continue;
                }
                JsonNode idNode = response.get("id");
                if (idNode != null && idNode.isInt()) {
                    int id = idNode.asInt();
                    CompletableFuture<JsonNode> future = pending.remove(id);
                    if (future == null) {
                        log.warn("No pending request for id={}", id);
                        continue;
                    }
                    JsonNode error = response.get("error");
                    if (error != null) {
                        future.completeExceptionally(
                                new RuntimeException("JSON-RPC error: " + error));
                    } else {
                        future.complete(response.get("result"));
                    }
                } else {
                    log.debug("Event (no id): {}", line);
                }
            }
        } catch (IOException e) {
            if (!closing) {
                log.error("Reader thread error", e);
            }
        } finally {
            // fail all remaining futures
            for (var entry : pending.entrySet()) {
                entry.getValue().completeExceptionally(
                        new RuntimeException("RPC server closed connection"));
            }
            pending.clear();
        }
    }

    private void writerLoop() {
        try (var writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream()))) {
            String json;
            while ((json = writeQueue.take()) != null) {
                if (closing) break;
                writer.write(json);
                writer.newLine();
                writer.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            if (!closing) {
                log.error("Writer thread error", e);
            }
        }
    }

    private String readStderr() {
        if (process == null) return null;
        try (var err = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = err.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() {
        closing = true;
        if (process != null) {
            process.destroy();
        }
        if (readerThread != null) {
            try { readerThread.join(2000); } catch (InterruptedException ignored) {}
        }
        if (writerThread != null) {
            writerThread.interrupt();
            try { writerThread.join(2000); } catch (InterruptedException ignored) {}
        }
    }
}
