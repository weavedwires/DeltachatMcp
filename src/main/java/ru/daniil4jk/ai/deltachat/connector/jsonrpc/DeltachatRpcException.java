package ru.daniil4jk.ai.deltachat.connector.jsonrpc;

/** Ошибка RPC-вызова (вернул сервер или транспорт). */
public class DeltachatRpcException extends RuntimeException {

    private final String method;

    public DeltachatRpcException(String method, String message, Throwable cause) {
        super(method + ": " + message, cause);
        this.method = method;
    }

    public DeltachatRpcException(String method, String message) {
        this(method, message, null);
    }

    public String getMethod() {
        return method;
    }
}
