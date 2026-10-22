package com.example.wechat.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarkdownMessageTest {

    @Test
    void shouldExposeMarkdownType() {
        MarkdownMessage message = MarkdownMessage.of("## 告警");
        assertEquals("## 告警", message.content());
        assertEquals(com.example.wechat.core.enums.MsgType.MARKDOWN, message.msgType());
        assertEquals(message, message.body());
    }

    @Test
    void shouldRejectNullOrBlankContent() {
        assertThrows(IllegalArgumentException.class, () -> MarkdownMessage.of(null));
        assertThrows(IllegalArgumentException.class, () -> MarkdownMessage.of(""));
    }

    @Test
    void shouldRejectContentLongerThan4096Bytes() {
        String ok = "告".repeat(1365);      // 4095 字节
        String tooLong = "告".repeat(1366); // 4098 字节

        assertEquals(ok, MarkdownMessage.of(ok).content());
        assertThrows(IllegalArgumentException.class, () -> MarkdownMessage.of(tooLong));
    }
}
