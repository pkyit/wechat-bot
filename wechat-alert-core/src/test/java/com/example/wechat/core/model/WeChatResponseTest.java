package com.example.wechat.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeChatResponseTest {

    @Test
    void successOnlyWhenErrcodeIsZero() {
        assertTrue(new WeChatResponse(0, "ok", null).success());
        assertFalse(new WeChatResponse(93000, "invalid webhook url", null).success());
    }

    @Test
    void shouldDetectAllTokenFailureCodes() {
        assertTrue(new WeChatResponse(WeChatResponse.TOKEN_INVALID, "", null).tokenExpired());
        assertTrue(new WeChatResponse(WeChatResponse.TOKEN_EXPIRED, "", null).tokenExpired());
        assertTrue(new WeChatResponse(WeChatResponse.TOKEN_MISSING, "", null).tokenExpired());
        assertFalse(new WeChatResponse(93000, "", null).tokenExpired());
    }
}
