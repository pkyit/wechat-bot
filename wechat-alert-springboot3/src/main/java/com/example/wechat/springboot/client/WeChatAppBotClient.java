package com.example.wechat.springboot.client;

import com.example.wechat.core.exception.WeChatException;
import com.example.wechat.core.model.MarkdownMessage;
import com.example.wechat.core.model.TextMessage;
import com.example.wechat.core.model.WeChatMessage;
import com.example.wechat.core.model.WeChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 企业微信自建应用客户端（Spring Boot 版）.
 *
 * <p>支持按 userid 推送给指定成员。access_token 自动缓存，过期自动刷新重试。
 *
 * <p>线程安全，单例复用。
 */
public class WeChatAppBotClient {

    private static final Logger log = LoggerFactory.getLogger(WeChatAppBotClient.class);

    private static final String GET_TOKEN_URL =
            "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s";
    private static final String SEND_MSG_URL =
            "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=%s";
    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 300;

    private final String corpId;
    private final int agentId;
    private final String secret;
    private final RestClient restClient;

    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>(null);

    public WeChatAppBotClient(String corpId, String agentId, String secret, RestClient restClient) {
        this.corpId = corpId;
        this.agentId = Integer.parseInt(agentId);
        this.secret = secret;
        this.restClient = restClient;
    }

    /**
     * 发送消息给指定用户.
     *
     * @param toUser 接收人 userid，多个用 {@code |} 分隔；{@code "@all"} 表示全员
     */
    public WeChatResponse send(String toUser, WeChatMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", toUser);
        body.put("msgtype", message.msgType().code());
        body.put("agentid", agentId);
        body.put(message.msgType().code(), message.body());

        return doSend(body, false);
    }

    public WeChatResponse sendText(String toUser, String content) {
        return send(toUser, TextMessage.of(content));
    }

    public WeChatResponse sendMarkdown(String toUser, String content) {
        return send(toUser, MarkdownMessage.of(content));
    }

    @SuppressWarnings("unchecked")
    private WeChatResponse doSend(Map<String, Object> body, boolean retrying) {
        String token = getAccessToken();
        String url = SEND_MSG_URL.formatted(token);

        WeChatResponse response = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(WeChatResponse.class);

        if (response == null) {
            throw new WeChatException("自建应用返回空响应");
        }

        // token 失效 → 清缓存重试一次
        if (response.tokenExpired() && !retrying) {
            log.warn("access_token 失效，刷新后重试");
            tokenCache.set(null);
            return doSend(body, true);
        }

        if (!response.success()) {
            throw new WeChatException(response.errcode(), response.errmsg());
        }

        log.info("自建应用消息发送成功, invalidUser={}", response.invalidUser());
        return response;
    }

    @SuppressWarnings("unchecked")
    private String getAccessToken() {
        CachedToken cached = tokenCache.get();
        long now = Instant.now().getEpochSecond();

        if (cached != null && now < cached.expireAt() - TOKEN_REFRESH_MARGIN_SECONDS) {
            return cached.token();
        }

        String url = GET_TOKEN_URL.formatted(corpId, secret);
        Map<String, Object> result = restClient.get()
                .uri(url)
                .retrieve()
                .body(Map.class);

        if (result == null) {
            throw new WeChatException("获取 access_token 返回空响应");
        }

        int errcode = (int) result.get("errcode");
        if (errcode != 0) {
            throw new WeChatException(errcode, String.valueOf(result.get("errmsg")));
        }

        String token = (String) result.get("access_token");
        int expiresIn = (int) result.get("expires_in");
        long expireAt = Instant.now().getEpochSecond() + expiresIn;

        tokenCache.set(new CachedToken(token, expireAt));
        log.debug("获取新 access_token 成功，有效期 {} 秒", expiresIn);
        return token;
    }

    private record CachedToken(String token, long expireAt) {
    }
}
