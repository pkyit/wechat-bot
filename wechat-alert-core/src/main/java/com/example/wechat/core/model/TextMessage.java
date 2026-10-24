package com.example.wechat.core.model;

import com.example.wechat.core.enums.MsgType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文本消息.
 *
 * <p>群机器人与自建应用通用。群机器人支持 @ 指定人（手机号或 userid）和 @all。
 *
 * @param content            消息内容，最长 2048 字节（UTF-8）
 * @param mentionedList      @ 的 userid 列表（群机器人用 "@all" 表示@所有人）
 * @param mentionedMobileList @ 的手机号列表
 */
public record TextMessage(
        String content,
        @JsonProperty("mentioned_list") List<String> mentionedList,
        @JsonProperty("mentioned_mobile_list") List<String> mentionedMobileList
) implements WeChatMessage {

    /** 企业微信 text 消息内容上限（字节，UTF-8） */
    public static final int MAX_CONTENT_BYTES = 2048;

    /** 紧凑构造器：在对象创建时前置校验，避免超长消息发送到企业微信后才失败 */
    public TextMessage {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        int bytes = content.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_CONTENT_BYTES) {
            throw new IllegalArgumentException(
                    "text 消息内容 %d 字节，超过企业微信 %d 字节上限".formatted(bytes, MAX_CONTENT_BYTES));
        }
        mentionedList = mentionedList == null ? List.of() : List.copyOf(mentionedList);
        mentionedMobileList = mentionedMobileList == null ? List.of() : List.copyOf(mentionedMobileList);
    }

    public static TextMessage of(String content) {
        return new TextMessage(content, List.of(), List.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public MsgType msgType() {
        return MsgType.TEXT;
    }

    @Override
    public Object body() {
        return this;
    }

    public static final class Builder {
        private String content;
        private List<String> mentionedList = List.of();
        private List<String> mentionedMobileList = List.of();

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder mentionUsers(String... userIds) {
            this.mentionedList = List.of(userIds);
            return this;
        }

        public Builder mentionMobiles(String... mobiles) {
            this.mentionedMobileList = List.of(mobiles);
            return this;
        }

        public Builder mentionAll() {
            this.mentionedList = List.of("@all");
            return this;
        }

        public TextMessage build() {
            return new TextMessage(content, mentionedList, mentionedMobileList);
        }
    }
}
