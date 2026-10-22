package com.example.wechat.core.model;

import com.example.wechat.core.exception.WeChatException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldParseSuccessfulResponse() throws Exception {
        TokenResponse response = objectMapper.readValue("""
                {"errcode":0,"errmsg":"ok","access_token":"token-1","expires_in":7200}
                """, TokenResponse.class);

        assertEquals("token-1", response.requireAccessToken());
        assertEquals(7200, response.expiresInOrDefault());
    }

    @Test
    void shouldAcceptTokenWhenErrcodeIsMissing() throws Exception {
        // 部分网关/代理会省略 errcode，此时只要 token 存在就应视为成功
        TokenResponse response = objectMapper.readValue("""
                {"access_token":"token-2"}
                """, TokenResponse.class);

        assertEquals("token-2", response.requireAccessToken());
        assertEquals(TokenResponse.DEFAULT_EXPIRES_IN, response.expiresInOrDefault());
    }

    @Test
    void shouldThrowWhenErrcodeIsNotZero() throws Exception {
        TokenResponse response = objectMapper.readValue("""
                {"errcode":40013,"errmsg":"invalid corpid"}
                """, TokenResponse.class);

        WeChatException e = assertThrows(WeChatException.class, response::requireAccessToken);
        assertEquals(40013, e.errcode());
        assertTrue(e.getMessage().contains("invalid corpid"), e.getMessage());
    }

    @Test
    void shouldThrowWhenTokenIsMissing() throws Exception {
        TokenResponse response = objectMapper.readValue("""
                {"errcode":0,"errmsg":"ok"}
                """, TokenResponse.class);

        WeChatException e = assertThrows(WeChatException.class, response::requireAccessToken);
        assertTrue(e.getMessage().contains("缺少 access_token"), e.getMessage());
    }

    @Test
    void shouldFallbackToDefaultExpiresInWhenValueIsInvalid() {
        assertEquals(TokenResponse.DEFAULT_EXPIRES_IN, new TokenResponse(0, "ok", "t", null).expiresInOrDefault());
        assertEquals(TokenResponse.DEFAULT_EXPIRES_IN, new TokenResponse(0, "ok", "t", 0).expiresInOrDefault());
    }
}
