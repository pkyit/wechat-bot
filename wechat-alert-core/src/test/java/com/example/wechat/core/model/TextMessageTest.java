package com.example.wechat.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeContentOnlyWhenNoMentions() throws Exception {
        String json = objectMapper.writeValueAsString(TextMessage.of("hello"));

        assertTrue(json.contains("\"content\":\"hello\""), json);
        assertFalse(json.contains("mentioned_list"), "空 @ 列表应被省略: " + json);
        assertFalse(json.contains("mentioned_mobile_list"), "空 @ 列表应被省略: " + json);
        assertFalse(json.contains("msgType"), "不应把接口方法序列化进消息体: " + json);
    }

    @Test
    void shouldSerializeMentionsInSnakeCase() throws Exception {
        String json = objectMapper.writeValueAsString(TextMessage.builder()
                .content("告警")
                .mentionUsers("zhangsan")
                .mentionMobiles("13800138000")
                .build());

        assertTrue(json.contains("\"mentioned_list\":[\"zhangsan\"]"), json);
        assertTrue(json.contains("\"mentioned_mobile_list\":[\"13800138000\"]"), json);
    }

    @Test
    void mentionAllShouldUseAtAll() {
        TextMessage message = TextMessage.builder().content("通知").mentionAll().build();
        assertEquals(java.util.List.of("@all"), message.mentionedList());
        assertEquals(com.example.wechat.core.enums.MsgType.TEXT, message.msgType());
    }

    @Test
    void shouldRejectNullAndBlankContent() {
        assertThrows(IllegalArgumentException.class, () -> TextMessage.of(null));
        assertThrows(IllegalArgumentException.class, () -> TextMessage.of("   "));
    }

    @Test
    void shouldRejectContentLongerThan2048Bytes() {
        String ok = "告".repeat(682);      // 2046 字节
        String tooLong = "告".repeat(683); // 2049 字节

        assertEquals(ok, TextMessage.of(ok).content());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TextMessage.of(tooLong));
        assertTrue(e.getMessage().contains("2048"), e.getMessage());
    }
}
