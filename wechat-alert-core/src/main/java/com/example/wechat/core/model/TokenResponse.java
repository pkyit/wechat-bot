package com.example.wechat.core.model;

import com.example.wechat.core.exception.WeChatException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 获取 access_token 接口的响应.
 *
 * <p>企业微信 {@code /cgi-bin/gettoken} 成功时返回
 * {@code {"errcode":0,"errmsg":"ok","access_token":"...","expires_in":7200}}；
 * 失败时只返回 errcode / errmsg。
 *
 * <p>字段使用包装类型并按空值安全的方式解析，避免不同网关或代理返回缺失字段时出现
 * {@link NullPointerException} / {@link ClassCastException}。
 *
 * @param errcode     错误码，缺省视为 0
 * @param errmsg      错误信息
 * @param accessToken 访问令牌
 * @param expiresIn   有效期（秒），缺省按 7200 处理
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenResponse(
        @JsonProperty("errcode") Integer errcode,
        @JsonProperty("errmsg") String errmsg,
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Integer expiresIn
) {

    /** 企业微信 access_token 默认有效期（秒） */
    public static final int DEFAULT_EXPIRES_IN = 7200;

    /**
     * 校验响应并返回 access_token.
     *
     * @return 非空的 access_token
     * @throws WeChatException 接口返回错误码或缺少 access_token 时抛出
     */
    public String requireAccessToken() {
        if (errcode != null && errcode != 0) {
            throw new WeChatException(errcode, errmsg == null ? "" : errmsg);
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new WeChatException("获取 access_token 失败: 响应中缺少 access_token（errcode="
                    + errcode + ", errmsg=" + errmsg + "）");
        }
        return accessToken;
    }

    /** 有效期秒数，缺失或非法时回落到默认值. */
    public int expiresInOrDefault() {
        return expiresIn == null || expiresIn <= 0 ? DEFAULT_EXPIRES_IN : expiresIn;
    }
}
