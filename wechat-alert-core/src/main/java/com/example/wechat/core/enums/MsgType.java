package com.example.wechat.core.enums;

/**
 * 企业微信消息类型.
 *
 * <p>覆盖群机器人与自建应用共用的消息类型。
 * 群机器人额外支持 image / news / file / template_card 等，
 * 自建应用额外支持 textcard / miniprogram_notice 等，按需扩展。
 */
public enum MsgType {

    /** 纯文本 */
    TEXT("text"),
    /** Markdown */
    MARKDOWN("markdown");

    private final String code;

    MsgType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
