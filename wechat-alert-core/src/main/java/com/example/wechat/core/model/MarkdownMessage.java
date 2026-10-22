package com.example.wechat.core.model;

import com.example.wechat.core.enums.MsgType;

/**
 * Markdown 消息.
 *
 * <p>群机器人与自建应用通用。支持加粗、链接、代码块、引用等 Markdown 语法，
 * 是告警通知的首选格式。
 *
 * @param content Markdown 内容
 */
public record MarkdownMessage(String content) implements WeChatMessage {

    public static MarkdownMessage of(String content) {
        return new MarkdownMessage(content);
    }

    @Override
    public MsgType msgType() {
        return MsgType.MARKDOWN;
    }

    @Override
    public Object body() {
        return this;
    }
}
