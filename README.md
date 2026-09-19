# DeltachatMcp

MCP-сервер для [Delta Chat](https://delta.chat). Позволяет AI-агентам читать чаты, получать и отправлять сообщения, импортировать аккаунты из бэкапов через протокол [MCP](https://modelcontextprotocol.io).

## Требования

- **Java 21+** и **Maven**
- **`deltachat-rpc-server`** на `$PATH` — устанавливается через `uv`:
  ```bash
  uv tool install deltachat-rpc-server
  ```

## Сборка и запуск

```bash
mvn clean package               # сборка shaded JAR
java -jar target/deltachat-mcp.jar   # запуск (stdin/stdout)
```

MCP-клиент (OpenCode, Claude Desktop и т.д.) запускает этот JAR как подпроцесс.

## Конфигурация

| Параметр | Переменная окружения | System property | По умолчанию |
|---|---|---|---|
| Директория аккаунтов | `DC_ACCOUNTS_PATH` | `deltachat.accountsPath` | `./accounts` |

> **Важно:** 🔒 Если на машине уже запущен другой процесс Delta Chat (например, реактивный бот), он держит блокировку `accounts.lock` в стандартной директории. **Всегда задавайте `DC_ACCOUNTS_PATH`** в отдельную директорию для MCP-сервера — иначе сервер упадёт с ошибкой блокировки.

## MCP-инструменты

| Инструмент | Описание |
|---|---|
| `import_from_backup` | Импорт аккаунта из `.tar` бэкапа (`backupPath`, `passphrase?`) — обязателен при старте без аккаунтов |
| `list_chats` | Список чатов (`listFlags`: 0=все, 9=архив, 512=без групп; `query` для поиска) |
| `list_unread_chats` | Чаты с непрочитанными сообщениями |
| `get_unread_messages` | Первые N непрочитанных сообщений из чата (`chatId`, `n`, помечаются прочитанными) |
| `get_last_messages` | Последние N сообщений из чата (`chatId`, `n`); ошибка если есть непрочитанные — сперва вызвать `get_unread_messages` |
| `get_messages_batch` | Батч сообщений с отсчётом от последнего полученного (`chatId`, `n`, `offset`); `offset=0` — последние n, `offset=n` — предыдущие n; ошибка при непрочитанных |
| `get_message` | Сообщение по ID |
| `send_message` | Отправка сообщения (`chatId`, `message` с полями `text`, `html`, `file`, `viewtype`, `quotedMessageId`, `overrideSenderName`, `location`) |

## Типичный workflow

1. Агент подключается → сервер проверяет наличие аккаунтов
2. Если аккаунтов нет → агент вызывает `import_from_backup`
3. После импорта доступны все инструменты
4. Для чтения: `list_unread_chats` → для каждого чата `get_unread_messages(chatId, n)` → обработка
5. Для ответа в конкретный чат: `get_last_messages(chatId, n)` (только после обработки всех непрочитанных в этом чате)

## Тестирование

```bash
mvn clean test
```

15 тестов (6 интеграционных против реального `deltachat-rpc-server`, 9 unit с Mockito).

## Архитектура

```
JAR (stdin/stdout MCP-транспорт)
  └─ DeltachatMcpServer     ← MCP SDK, 8 tools
       └─ DeltachatServiceImpl   ← бизнес-логика, accountId
            └─ DeltachatRpcClient  ← JSON Lines над stdin/stdout подпроцесса
                 └─ deltachat-rpc-server  ← Rust-демон Delta Chat
```

- JSON-RPC 2.0, позиционные параметры (Rust `#[rpc(all_positional)]`)
- Модели (`FullChat`, `MessageObject`, `MessageData`, `ContactObject`) зеркалируют типы из [deltachat-jsonrpc](https://github.com/chatmail/core/tree/main/deltachat-jsonrpc/src/api/types)
