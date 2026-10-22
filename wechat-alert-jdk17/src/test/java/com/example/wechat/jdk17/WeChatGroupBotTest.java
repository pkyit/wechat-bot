package com.example.wechat.jdk17;

import com.example.wechat.core.exception.WeChatException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeChatGroupBotTest {

    @Test
    void shouldPostMarkdownWithWebhookKey() throws Exception {
        try (TestWeComServer server = new TestWeComServer()) {
            WeChatGroupBot bot = new WeChatGroupBot(server.baseUrl() + "/cgi-bin/webhook/send?key=test-key");

            assertTrue(bot.sendMarkdown("## 告警\n服务异常"));

            TestWeComServer.Recorded request = server.request(0);
            assertEquals("POST", request.method());
            assertEquals("/cgi-bin/webhook/send", request.path());
            assertEquals("key=test-key", request.query());
            assertTrue(request.body().contains("\"msgtype\":\"markdown\""), request.body());
            assertTrue(request.body().contains("\"markdown\":{\"content\""), request.body());
            assertTrue(request.body().contains("## 告警"), request.body());
        }
    }

    @Test
    void shouldSerializeMentionsForTextMessage() throws Exception {
        try (TestWeComServer server = new TestWeComServer()) {
            WeChatGroupBot bot = new WeChatGroupBot(server.baseUrl() + "/webhook");

            bot.sendText("CPU 告警", "13800138000");

            String body = server.request(0).body();
            assertTrue(body.contains("\"msgtype\":\"text\""), body);
            assertTrue(body.contains("\"mentioned_mobile_list\":[\"13800138000\"]"), body);
        }
    }

    @Test
    void shouldThrowWithWeComErrcodeOnBusinessFailure() throws Exception {
        try (TestWeComServer server = new TestWeComServer()) {
            server.enqueue("{\"errcode\":93000,\"errmsg\":\"invalid webhook url\"}");
            WeChatGroupBot bot = new WeChatGroupBot(server.baseUrl() + "/webhook");

            WeChatException e = assertThrows(WeChatException.class, () -> bot.sendText("告警"));
            assertEquals(93000, e.errcode());
            assertTrue(e.getMessage().contains("invalid webhook url"), e.getMessage());
        }
    }

    @Test
    void shouldThrowOnHttpError() throws Exception {
        try (TestWeComServer server = new TestWeComServer()) {
            server.enqueue(new TestWeComServer.Reply(502, "bad gateway"));
            WeChatGroupBot bot = new WeChatGroupBot(server.baseUrl() + "/webhook");

            WeChatException e = assertThrows(WeChatException.class, () -> bot.sendText("告警"));
            assertTrue(e.getMessage().contains("HTTP 502"), e.getMessage());
        }
    }
}
