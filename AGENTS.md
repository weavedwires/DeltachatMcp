# DeltachatMcp — MCP server for Delta Chat

## Stack

- Java 21, Maven, Lombok, SLF4J simple
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) v0.8.0 (no Spring — standalone sync server)
- **No jsonrpc4j in use** — custom JSON-RPC 2.0 client over subprocess stdin/stdout
- Communication: **stdin/stdout** (`StdioServerTransportProvider`) — no HTTP port

## Build & Test

```bash
mvn clean test          # compile + unit + integration tests
mvn clean package       # build shaded JAR to target/deltachat-mcp.jar
java -jar target/deltachat-mcp.jar   # MCP client spawns this as subprocess
```

## Architecture

```
DeltachatMcpApplication.main()
  └─ DeltachatMcpServer          ← MCP SDK: exposes 7 tools (import_from_backup,
  │                                 list_chats, list_unread_chats,
  │                                 get_unread_messages, get_last_messages,
  │                                 get_message, send_message)
  └─ DeltachatServiceImpl         ← wraps DeltachatRpcClient, manages accountId
       └─ DeltachatRpcClient      ← launches deltachat-rpc-server subprocess,
                                    JSON Lines over stdin/stdout with
                                    reader/writer threads + CompletableFuture dispatch
```

- Package: `ru.daniil4jk.ai.deltachat.connector`
- Models mirror Delta Chat JSON-RPC types exactly: `FullChat`, `MessageObject` (read-only response), `MessageData` (write-only send params), `ContactObject`
- All models use `@JsonIgnoreProperties(ignoreUnknown = true)` — extra fields from future API versions are silently ignored
- JSON-RPC params are **positional arrays** (Rust `#[rpc(all_positional)]`)
- `UnreadMessagesExistException` forces agents to call `get_unread_messages` before `get_last_messages`

## Startup flow

1. `DeltachatRpcClient.start()` spawns `deltachat-rpc-server` subprocess, starts reader/writer threads, performs health-check via `get_system_info()`
2. `DeltachatServiceImpl.init()` tries `get_selected_account_id()` → `get_all_account_ids()` → first available
3. If no accounts → tools return error; agent must call `import_from_backup` first
4. `import_from_backup(path, passphrase?)` → `add_account()` + `import_backup()` + `start_io()`

## Key conventions

- `deltachat-rpc-server` must be on `$PATH` (uv tool `deltachat-rpc-server`)
- Accounts dir: `DC_ACCOUNTS_PATH` env var or `deltachat.accountsPath` system property, defaults to `./accounts`
- Chat list flags: `0` = all, `9` = archived, `512` = no groups
- Message states use `MessageState` int constants (`IN_FRESH=10`, `OUT_PENDING=20`, etc.)

## Test conventions

- `DeltachatRpcClientTest` — integration tests against real `deltachat-rpc-server` with temp accounts dir
- `DeltachatServiceImplTest` — Mockito unit tests for service logic and error handling
- All 15 tests must pass: `mvn test`
