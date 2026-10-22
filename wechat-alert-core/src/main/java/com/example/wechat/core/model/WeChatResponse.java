package com.example.wechat.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 企业微信 API 统一响应.
 *
 * <p>errcode = 0 表示成功，其余为失败（详见企业微信错误码文档）。
 * 用 record 表达不可变值对象，天然线程安全。
 *
 * @param errcode 错误码，0 表示成功
 * @param errmsg  错误信息
 * @param invalidUser 发送失败的用户 userid（仅自建应用消息接口返回）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WeChatResponse(
        @JsonProperty("errcode") int errcode,
        @JsonProperty("errmsg") String errmsg,
        @JsonProperty("invaliduser") String invalidUser
) {

    /** 成功错误码 */
    public static final int OK = 0;

    /** access_token 失效错误码 */
    public static final int TOKEN_INVALID = 40014;
    public static final int TOKEN_EXPIRED = 42001;

    public boolean success() {
        return errcode == OK;
    }

    public boolean tokenExpired() {
        return errcode == TOKEN_INVALID || errcode == TOKEN_EXPIRED;
    }
}
