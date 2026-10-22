package com.example.wechat.core.model;

import com.example.wechat.core.enums.MsgType;

/**
 * Markdown 消息.
 *
 * <p>群机器人与自建应用通用。支持加粗、链接、代码块、引用等 Markdown 语法，
 * 是告警通知的首选格式。
 *
 * <p>不可变值对象，构造时校验内容非空且不超过企业微信限制的 4096 字节（UTF-8）。
 *
 * @param content Markdown 内容，最长 4096 字节（UTF-8）
 */
public record MarkdownMessage(String content) implements WeChatMessage {

    /** Markdown 内容上限（字节，UTF-8） */
    public static final int MAX_CONTENT_BYTES = 4096;

    public MarkdownMessage {
        content = TextMessage.requireValidContent(content, MAX_CONTENT_BYTES);
    }

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
