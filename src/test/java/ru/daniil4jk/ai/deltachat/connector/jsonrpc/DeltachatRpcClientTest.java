package ru.daniil4jk.ai.deltachat.connector.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DeltachatRpcClientTest {

    private DeltachatRpcClient client;

    @TempDir
    Path accountsDir;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("deltachat.accountsPath", accountsDir.toString());
        client = new DeltachatRpcClient(new ObjectMapper());
        client.start();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void healthCheckWorks() {
        JsonNode info = client.call("get_system_info");
        assertNotNull(info);
        assertTrue(info.has("deltachat_core_version"));
    }

    @Test
    void noAccountsInitially() {
        JsonNode ids = client.call("get_all_account_ids");
        assertNotNull(ids);
        assertTrue(ids.isArray());
        assertEquals(0, ids.size());
    }

    @Test
    void selectedAccountIsNullInitially() {
        JsonNode selected = client.call("get_selected_account_id");
        assertTrue(selected == null || selected.isNull());
    }

    @Test
    void addAccountReturnsId() {
        JsonNode id = client.call("add_account");
        assertNotNull(id);
        assertTrue(id.isInt());
        assertTrue(id.asInt() > 0);

        JsonNode all = client.call("get_all_account_ids");
        assertEquals(1, all.size());
        assertEquals(id.asInt(), all.get(0).asInt());
    }

    @Test
    void methodNotFoundReturnsError() {
        var ex = assertThrows(RuntimeException.class,
                () -> client.call("nonexistent_method"));
        assertTrue(ex.getMessage().contains("JSON-RPC error"));
    }

    @Test
    void unknownAccountReturnsError() {
        var ex = assertThrows(RuntimeException.class,
                () -> client.call("get_info", 99999));
        assertTrue(ex.getMessage().contains("JSON-RPC error") ||
                   ex.getMessage().contains("account"));
    }
}
