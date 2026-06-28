package ru.daniil4jk.ai.deltachat.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.daniil4jk.ai.deltachat.connector.jsonrpc.DeltachatRpcClient;
import ru.daniil4jk.ai.deltachat.connector.model.FullChat;
import ru.daniil4jk.ai.deltachat.connector.model.MessageData;
import ru.daniil4jk.ai.deltachat.connector.model.MessageObject;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeltachatServiceImplTest {

    @Mock
    private DeltachatRpcClient rpc;

    private ObjectMapper mapper;
    private DeltachatServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new DeltachatServiceImpl(rpc, mapper);
    }

    @Test
    void initSelectsExistingAccount() {
        when(rpc.call("get_selected_account_id")).thenReturn(null);
        var ids = mapper.createArrayNode();
        ids.add(5);
        when(rpc.call("get_all_account_ids")).thenReturn(ids);

        service.init();
        assertTrue(service.hasAccount());

        // listChats should succeed
        var entries = mapper.createArrayNode();
        entries.add(10);
        when(rpc.call("get_chatlist_entries", 5, 0, null, null)).thenReturn(entries);
        var chatJson = mapper.createObjectNode();
        chatJson.put("id", 10);
        chatJson.put("name", "Test");
        chatJson.put("chatType", "Single");
        when(rpc.call("get_full_chat_by_id", 5, 10)).thenReturn(chatJson);

        List<FullChat> chats = service.listChats(0, null);
        assertEquals(1, chats.size());
        assertEquals(10, chats.get(0).getId());
        assertEquals("Test", chats.get(0).getName());
    }

    @Test
    void initNoAccount() {
        when(rpc.call("get_selected_account_id")).thenReturn(null);
        var ids = mapper.createArrayNode();
        when(rpc.call("get_all_account_ids")).thenReturn(ids);

        service.init();
        assertFalse(service.hasAccount());
    }

    @Test
    void importFromBackupCreatesAccount() {
        when(rpc.call("add_account")).thenReturn(mapper.convertValue(1, JsonNode.class));
        when(rpc.call("import_backup", 1, "/path/to.tar", "")).thenReturn(null);
        when(rpc.call("start_io", 1)).thenReturn(null);

        int accountId = service.importFromBackup("/path/to.tar", null);
        assertEquals(1, accountId);
        assertTrue(service.hasAccount());
    }

    @Test
    void listUnreadChatsFiltersByFreshCounter() {
        when(rpc.call("get_selected_account_id")).thenReturn(mapper.convertValue(1, JsonNode.class));
        service.init();

        var entries = mapper.createArrayNode();
        entries.add(10);
        entries.add(20);
        when(rpc.call("get_chatlist_entries", 1, 0, null, null)).thenReturn(entries);

        var chat1 = mapper.createObjectNode();
        chat1.put("id", 10);
        chat1.put("name", "Chat 1");
        chat1.put("chatType", "Single");
        chat1.put("freshMessageCounter", 0);
        when(rpc.call("get_full_chat_by_id", 1, 10)).thenReturn(chat1);

        var chat2 = mapper.createObjectNode();
        chat2.put("id", 20);
        chat2.put("name", "Chat 2");
        chat2.put("chatType", "Group");
        chat2.put("freshMessageCounter", 3);
        when(rpc.call("get_full_chat_by_id", 1, 20)).thenReturn(chat2);

        List<FullChat> unread = service.listUnreadChats();
        assertEquals(1, unread.size());
        assertEquals(20, unread.get(0).getId());
    }

    @Test
    void getUnreadMessagesFetchesAndMarksSeen() {
        when(rpc.call("get_selected_account_id")).thenReturn(mapper.convertValue(1, JsonNode.class));
        service.init();

        var freshIds = mapper.createArrayNode();
        freshIds.add(100);
        freshIds.add(101);
        freshIds.add(200);
        when(rpc.call("get_fresh_msgs", 1)).thenReturn(freshIds);

        var msgsMap = mapper.createObjectNode();
        // chat 10 messages
        var msg100 = mapper.createObjectNode();
        msg100.put("id", 100);
        msg100.put("chatId", 10);
        msg100.put("text", "Hello");
        msg100.put("viewType", "Text");
        msg100.put("state", 10);
        var msg101 = mapper.createObjectNode();
        msg101.put("id", 101);
        msg101.put("chatId", 10);
        msg101.put("text", "World");
        msg101.put("viewType", "Text");
        msg101.put("state", 10);
        // another chat message
        var msg200 = mapper.createObjectNode();
        msg200.put("id", 200);
        msg200.put("chatId", 20);
        msg200.put("text", "Other");
        msg200.put("viewType", "Text");
        msg200.put("state", 10);

        var loadResult100 = mapper.createObjectNode();
        loadResult100.set("message", msg100);
        var loadResult101 = mapper.createObjectNode();
        loadResult101.set("message", msg101);
        var loadResult200 = mapper.createObjectNode();
        loadResult200.set("message", msg200);
        msgsMap.set("100", loadResult100);
        msgsMap.set("101", loadResult101);
        msgsMap.set("200", loadResult200);

        when(rpc.call("get_messages", 1, List.of(100, 101, 200))).thenReturn(msgsMap);
        when(rpc.call("markseen_msgs", 1, List.of(100, 101))).thenReturn(null);

        List<MessageObject> msgs = service.getUnreadMessages(10, 10);
        assertEquals(2, msgs.size());
        assertEquals("Hello", msgs.get(0).getText());
        assertEquals("World", msgs.get(1).getText());
    }

    @Test
    void getLastMessagesThrowsOnUnread() {
        when(rpc.call("get_selected_account_id")).thenReturn(mapper.convertValue(1, JsonNode.class));
        service.init();

        var chatNode = mapper.createObjectNode();
        chatNode.put("id", 10);
        chatNode.put("name", "Chat");
        chatNode.put("freshMessageCounter", 2);
        when(rpc.call("get_full_chat_by_id", 1, 10)).thenReturn(chatNode);

        assertThrows(UnreadMessagesExistException.class,
                () -> service.getLastMessages(10, 5));
    }

    @Test
    void sendMessageCallsRpc() {
        when(rpc.call("get_selected_account_id")).thenReturn(mapper.convertValue(1, JsonNode.class));
        service.init();

        var data = new MessageData();
        data.setText("Hi");
        data.setHtml("<b>Hi</b>");

        when(rpc.call("send_msg", 1, 10, data))
                .thenReturn(mapper.convertValue(200, JsonNode.class));

        int msgId = service.sendMessage(10, data);
        assertEquals(200, msgId);
    }

    @Test
    void getMessageByFetchesFromRpc() {
        when(rpc.call("get_selected_account_id")).thenReturn(mapper.convertValue(1, JsonNode.class));
        service.init();

        var msgNode = mapper.createObjectNode();
        msgNode.put("id", 42);
        msgNode.put("chatId", 10);
        msgNode.put("text", "Test message");
        msgNode.put("viewType", "Text");
        msgNode.put("state", 13);
        when(rpc.call("get_message", 1, 42)).thenReturn(msgNode);

        MessageObject msg = service.getMessage(42);
        assertEquals(42, msg.getId());
        assertEquals("Test message", msg.getText());
    }

    @Test
    void serviceThrowsWithoutAccount() {
        var ex = assertThrows(IllegalStateException.class,
                () -> service.listChats(0, null));
        assertTrue(ex.getMessage().contains("import_from_backup"));
    }
}
