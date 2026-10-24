package com.example.wechat.core.model;

import com.example.wechat.core.enums.MsgType;

import java.nio.charset.StandardCharsets;

/**
 * Markdown 消息.
 *
 * <p>群机器人与自建应用通用。支持加粗、链接、代码块、引用等 Markdown 语法，
 * 是告警通知的首选格式。
 *
 * @param content Markdown 内容，最长 4096 字节（UTF-8）
 */
public record MarkdownMessage(String content) implements WeChatMessage {

    /** 企业微信 markdown 消息内容上限（字节，UTF-8） */
    public static final int MAX_CONTENT_BYTES = 4096;

    /** 紧凑构造器：在对象创建时前置校验，避免超长消息发送到企业微信后才失败 */
    public MarkdownMessage {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        int bytes = content.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_CONTENT_BYTES) {
            throw new IllegalArgumentException(
                    "markdown 消息内容 %d 字节，超过企业微信 %d 字节上限".formatted(bytes, MAX_CONTENT_BYTES));
        }
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
