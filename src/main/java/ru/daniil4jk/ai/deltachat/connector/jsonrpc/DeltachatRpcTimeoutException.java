package ru.daniil4jk.ai.deltachat.connector.jsonrpc;

/** Вызов RPC не уложился в таймаут: сервер жив, но не ответил. */
public class DeltachatRpcTimeoutException extends DeltachatRpcException {

    private final long timeoutMs;
    private final String stderrTail;

    public DeltachatRpcTimeoutException(String method, long timeoutMs, String stderrTail) {
        super(method, "no response in " + timeoutMs + " ms"
                + (stderrTail == null || stderrTail.isBlank() ? "" : "; stderr tail: " + stderrTail));
        this.timeoutMs = timeoutMs;
        this.stderrTail = stderrTail;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public String getStderrTail() {
        return stderrTail;
    }
}
