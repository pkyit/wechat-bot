package com.example.wechat.springboot.client;

import com.example.wechat.core.exception.WeChatException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WeChatAppBotClientTest {

    private static final String CORP_ID = "ww-test-corp";
    private static final String SECRET = "test-secret";
    private static final String AGENT_ID = "1000002";

    private static final String TOKEN_URL =
            "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + CORP_ID + "&corpsecret=" + SECRET;
    private static final String SEND_URL = "https://qyapi.weixin.qq.com/cgi-bin/message/send";

    private MockRestServiceServer server;
    private WeChatAppBotClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WeChatAppBotClient(CORP_ID, AGENT_ID, SECRET, builder.build());
    }

    private static String tokenJson(String token) {
        return "{\"errcode\":0,\"errmsg\":\"ok\",\"access_token\":\"" + token + "\",\"expires_in\":7200}";
    }

    @Test
    void shouldCacheTokenAcrossSends() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(tokenJson("token-1"), MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.twice(), requestTo(SEND_URL + "?access_token=token-1"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertEquals(0, client.sendText("zhangsan", "第一条").errcode());
        assertEquals(0, client.sendText("zhangsan", "第二条").errcode());
        server.verify();
    }

    @Test
    void shouldRefreshTokenAndRetryOnceWhenExpired() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess(tokenJson("token-1"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_URL + "?access_token=token-1"))
                .andRespond(withSuccess("{\"errcode\":42001,\"errmsg\":\"access_token expired\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess(tokenJson("token-2"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_URL + "?access_token=token-2"))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));

        assertEquals(0, client.sendText("zhangsan", "重试").errcode());
        server.verify();
    }

    @Test
    void shouldThrowWithErrcodeOnBusinessFailure() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess(tokenJson("token-1"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_URL + "?access_token=token-1"))
                .andRespond(withSuccess("{\"errcode\":81013,\"errmsg\":\"user invalid\"}",
                        MediaType.APPLICATION_JSON));

        WeChatException e = assertThrows(WeChatException.class, () -> client.sendText("nobody", "告警"));
        assertEquals(81013, e.errcode());
    }

    @Test
    void shouldThrowWhenTokenResponseHasNoToken() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));

        WeChatException e = assertThrows(WeChatException.class, () -> client.sendText("zhangsan", "告警"));
        assertTrue(e.getMessage().contains("access_token"), e.getMessage());
    }

    @Test
    void shouldRejectInvalidAgentId() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new WeChatAppBotClient(CORP_ID, "abc", SECRET, RestClient.create()));
        assertTrue(e.getMessage().contains("agentId"), e.getMessage());
    }
}
