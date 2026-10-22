package com.example.wechat.jdk17;

import com.example.wechat.core.exception.WeChatException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeChatAppBotTest {

    private static final String CORP_ID = "ww-test-corp";
    private static final String SECRET = "test-secret";
    private static final int AGENT_ID = 1000002;

    private static String tokenReply(String token) {
        return "{\"errcode\":0,\"errmsg\":\"ok\",\"access_token\":\"" + token
                + "\",\"expires_in\":7200}";
    }

    @Test
    void shouldCacheTokenAcrossSends() throws Exception {
        try (TestWeComServer server = new TestWeComServer()) {
            server.enqueue(tokenReply("token-1"));
            WeChatAppBot bot = new WeChatAppBot(CORP_ID, AGENT_ID, SECRET, new WeChatHttpClient(), server.baseUrl());

            bot.sendText("zhangsan", "第一条");
            bot.sendText("zhangsan", "第二条");

            List<TestWeComServer.Recorded> requests = server.requests();
            assertEquals(3, requests.size(), "两次发送只应获取一次 token");
            assertEquals("/cgi-bin/gettoken", requests.get(0).path());
            assertEquals("corpid=" + CORP_ID + "&corpsecret=" + SECRET, requests.get(0).query());
            assertEquals("/cgi-bin/message/send", requests.get(1).path());
            assertEquals("access_token=token-1", requests.get(1).query());
            assertTrue(requests.get(1).body().contains("\"touser\":\"zhangsan\""), requests.get(1).body());
            assertTrue(requests.get(1).body().contains("\"agentid\":" + AGENT_ID), requests.get(1).body());
            assertTrue(requests.get(1).body().contains("\"msgtype\":\"text\""), requests.get(1).body());
            assertEquals("access_token=token-1", requests.get(2).query());
        }
    }

    @Test
    void shouldRefreshTokenAndRetryOnceWhenExpired() throws Exception {
        try (TestWeComServer server = new TestWeComServer()) {
            server.enqueue(tokenReply("token-1"))
                    .enqueue("{\"errcode\":42001,\"errmsg\":\"access_token expired\"}")
                    .enqueue(tokenReply("token-2"));
            WeChatAppBot bot = new WeChatAppBot(CORP_ID, AGENT_ID, SECRET, new WeChatHttpClient(), server.baseUrl());

            assertTrue(bot.sendText("zhangsan", "重试后成功"));

            List<TestWeComServer.Recorded> requests = server.requests();
            assertEquals(4, requests.size());
            assertEquals("access_token=token-1", requests.get(1).query());
            assertEquals("/cgi-bin/gettoken", requests.get(2).path());
            assertEquals("access_token=token-2", requests.get(3).query());
        }
    }

    @Test
    void shouldThrowWhenTokenEndpointFails() throws Exception {
        try (TestWeComServer server = new TestWeComServer()) {
            server.enqueue("{\"errcode\":40013,\"errmsg\":\"invalid corpid\"}");
            WeChatAppBot bot = new WeChatAppBot(CORP_ID, AGENT_ID, SECRET, new WeChatHttpClient(), server.baseUrl());

            WeChatException e = assertThrows(WeChatException.class, () -> bot.sendText("zhangsan", "告警"));
            assertEquals(40013, e.errcode());
        }
    }

    @Test
    void shouldThrowOnBusinessFailure() throws Exception {
        try (TestWeComServer server = new TestWeComServer()) {
            server.enqueue(tokenReply("token-1"))
                    .enqueue("{\"errcode\":81013,\"errmsg\":\"user & party & tag all invalid\"}");
            WeChatAppBot bot = new WeChatAppBot(CORP_ID, AGENT_ID, SECRET, new WeChatHttpClient(), server.baseUrl());

            WeChatException e = assertThrows(WeChatException.class, () -> bot.sendText("nobody", "告警"));
            assertEquals(81013, e.errcode());
        }
    }

    @Test
    void shouldRejectInvalidAgentId() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new WeChatAppBot(CORP_ID, "not-a-number", SECRET));
        assertTrue(e.getMessage().contains("agentId"), e.getMessage());
    }

    @Test
    void shouldRejectBlankToUser() throws Exception {
        try (TestWeComServer server = new TestWeComServer()) {
            WeChatAppBot bot = new WeChatAppBot(CORP_ID, AGENT_ID, SECRET, new WeChatHttpClient(), server.baseUrl());
            assertThrows(IllegalArgumentException.class, () -> bot.sendText(" ", "告警"));
        }
    }
}
