package ru.daniil4jk.ai.deltachat.connector.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Клиент deltachat-rpc-server.
 * <p>
 * Протокол (JSON-RPC 2.0, кадры — по одному JSON на строку) обслуживает библиотека
 * LSP4J: она ведёт корреляцию id, разбирает ответы/ошибки и не ломается на
 * server→client уведомлениях. Здесь остаётся только то, чего в библиотеке нет:
 * жизненный цикл процесса, дренаж stderr и жёсткий таймаут на вызов.
 */
public class DeltachatRpcClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(DeltachatRpcClient.class);
    private static final int STDERR_TAIL_LINES = 40;

    private final ObjectMapper objectMapper;
    /** Gson нужен только для позиционных параметров: LSP4J не трогает готовый JsonElement. */
    private final Gson gson = new Gson();
    private final String rpcServerPath;
    private final String[] extraArgs;
    private final String accountsPath;
    private final long callTimeoutMs;

    private Process process;
    private RemoteEndpoint endpoint;
    private MessageJsonHandler jsonHandler;
    private Thread readerThread;
    private Thread stderrThread;
    private final Deque<String> stderrTail = new ArrayDeque<>();
    private volatile boolean closing;
    private final Object writeLock = new Object();

    public DeltachatRpcClient(ObjectMapper objectMapper) {
        this(objectMapper, "deltachat-rpc-server");
    }

    public DeltachatRpcClient(ObjectMapper objectMapper, String rpcServerPath, String... extraArgs) {
        this.objectMapper = objectMapper;
        this.rpcServerPath = rpcServerPath;
        this.extraArgs = extraArgs != null ? extraArgs : new String[0];
        String envPath = System.getenv("DC_ACCOUNTS_PATH");
        if (envPath == null || envPath.isBlank()) {
            envPath = System.getProperty("deltachat.accountsPath", "accounts");
        }
        this.accountsPath = envPath;
        this.callTimeoutMs = Long.getLong("deltachat.rpcTimeoutMs",
                parseLong(System.getenv("DC_RPC_TIMEOUT_MS"), 120_000L));
    }

    private static long parseLong(String value, long def) {
        if (value == null || value.isBlank()) return def;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void start() throws IOException {
        log.info("Starting deltachat-rpc-server: {} (accounts={}, timeout={}ms)",
                rpcServerPath, accountsPath, callTimeoutMs);
        var cmd = new String[1 + extraArgs.length];
        cmd[0] = rpcServerPath;
        System.arraycopy(extraArgs, 0, cmd, 1, extraArgs.length);

        var pb = new ProcessBuilder(cmd);
        pb.environment().put("DC_ACCOUNTS_PATH", accountsPath);
        pb.redirectErrorStream(false);
        process = pb.start();

        startStderrDrain();
        startEndpoint();

        try {
            call("get_system_info");
        } catch (Exception e) {
            throw new IOException("RPC server failed to start: " + e.getMessage()
                    + (stderrTail.isEmpty() ? "" : "\nstderr:\n" + String.join("\n", stderrTail)), e);
        }
        log.info("deltachat-rpc-server is ready");
    }

    /** Создаёт RemoteEndpoint LSP4J поверх stdin/stdout процесса и запускает чтение. */
    private void startEndpoint() {
        // Регистрируем контракт: без него LSP4J не знает тип result и возвращает null,
        // а MethodProvider (сам endpoint) позволяет сопоставить id ответа с методом.
        Map<String, JsonRpcMethod> supportedMethods = ServiceEndpoints.getSupportedMethods(DeltaChatApi.class);
        jsonHandler = new MessageJsonHandler(supportedMethods);
        OutputStream out = process.getOutputStream();
        MessageConsumer consumer = message -> {
            String json = jsonHandler.serialize(message);
            synchronized (writeLock) {
                try {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                    out.write('\n');
                    out.flush();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write RPC message: " + e.getMessage(), e);
                }
            }
        };
        // локальный сервис пустой: deltachat-rpc-server не присылает запросов, только уведомления
        Endpoint localService = new Endpoint() {
            @Override
            public CompletableFuture<?> request(String method, Object parameter) {
                CompletableFuture<Object> failed = new CompletableFuture<>();
                failed.completeExceptionally(
                        new UnsupportedOperationException("Unexpected request from server: " + method));
                return failed;
            }

            @Override
            public void notify(String method, Object parameter) {
                log.debug("server notification: {} {}", method, parameter);
            }
        };
        endpoint = new RemoteEndpoint(consumer, localService);
        endpoint.setJsonHandler(jsonHandler);
        jsonHandler.setMethodProvider(endpoint);

        readerThread = new Thread(this::readerLoop, "dc-rpc-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /** Один JSON на строку; парсинг и корреляцию ответов делает LSP4J. */
    private void readerLoop() {
        try (var reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                log.trace("<- {}", line);
                Message message;
                try {
                    message = jsonHandler.parseMessage(line);
                } catch (Exception e) {
                    log.warn("Unparsable RPC message ({} bytes): {}", line.length(), abbreviate(line), e);
                    continue;
                }
                try {
                    endpoint.consume(message);
                } catch (Exception e) {
                    log.warn("Failed to handle RPC message", e);
                }
                if (message instanceof ResponseMessage ignored) {
                    // nothing: futures already completed by LSP4J
                }
            }
        } catch (IOException e) {
            if (!closing) log.error("Reader thread error", e);
        } finally {
            if (!closing) {
                Integer code = null;
                try {
                    code = process.waitFor(2, TimeUnit.SECONDS) ? process.exitValue() : null;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                log.error("deltachat-rpc-server closed stdout (exit={})", code);
            }
        }
    }

    private void startStderrDrain() {
        stderrThread = new Thread(() -> {
            try (var err = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = err.readLine()) != null) {
                    log.info("dc-rpc: {}", line);
                    synchronized (stderrTail) {
                        stderrTail.addLast(line);
                        while (stderrTail.size() > STDERR_TAIL_LINES) stderrTail.removeFirst();
                    }
                }
            } catch (IOException e) {
                if (!closing) log.warn("stderr drain stopped: {}", e.getMessage());
            }
        }, "dc-rpc-stderr");
        stderrThread.setDaemon(true);
        stderrThread.start();
    }

    /**
     * Вызов метода RPC-сервера. Параметры всегда передаются позиционно (JSON-массив),
     * как требует deltachat-rpc-server.
     */
    public JsonNode call(String method, Object... params) {
        if (endpoint == null) throw new IllegalStateException("RPC client is not started");
        ensureAlive(method);

        List<Object> positional = params == null ? List.of() : Arrays.asList(params);
        // Готовый JsonArray проходит через LSP4J без переупаковки (иначе одиночный
        // параметр оборачивается в [[x]], а deltachat-rpc-server ждёт плоский массив).
        JsonElement paramsJson = gson.toJsonTree(positional);
        CompletableFuture<Object> future = endpoint.request(method, paramsJson);

        try {
            Object result = future.get(callTimeoutMs, TimeUnit.MILLISECONDS);
            return result == null ? objectMapper.nullNode() : objectMapper.valueToTree(result);
        } catch (TimeoutException e) {
            future.cancel(true);
            String tail = stderrTail();
            log.error("RPC timeout after {} ms on {} (stderr tail: {})", callTimeoutMs, method, tail);
            throw new DeltachatRpcTimeoutException(method, callTimeoutMs, tail);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ResponseErrorException ree) {
                throw new DeltachatRpcException(method,
                        ree.getResponseError() == null ? ree.getMessage() : String.valueOf(ree.getResponseError()), cause);
            }
            if (cause instanceof RuntimeException re) throw re;
            throw new DeltachatRpcException(method, String.valueOf(cause), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeltachatRpcException(method, "interrupted", e);
        }
    }

    private void ensureAlive(String method) {
        if (process != null && !process.isAlive()) {
            throw new DeltachatRpcException(method, "deltachat-rpc-server is not running (exit="
                    + process.exitValue() + ")" + (stderrTail.isEmpty() ? "" : ", stderr: " + stderrTail()));
        }
    }

    private String stderrTail() {
        synchronized (stderrTail) {
            return stderrTail.isEmpty() ? "" : String.join(" | ", stderrTail);
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    public long getCallTimeoutMs() {
        return callTimeoutMs;
    }

    @Override
    public void close() {
        closing = true;
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
