package com.example.wechat.core.model;

import com.example.wechat.core.enums.MsgType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 文本消息.
 *
 * <p>群机器人与自建应用通用。群机器人支持 @ 指定人（手机号或 userid）和 @all。
 *
 * @param content            消息内容，最长 2048 字节
 * @param mentionedList      @ 的 userid 列表（群机器人用 "@all" 表示@所有人）
 * @param mentionedMobileList @ 的手机号列表
 */
public record TextMessage(
        String content,
        @JsonProperty("mentioned_list") List<String> mentionedList,
        @JsonProperty("mentioned_mobile_list") List<String> mentionedMobileList
) implements WeChatMessage {

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
