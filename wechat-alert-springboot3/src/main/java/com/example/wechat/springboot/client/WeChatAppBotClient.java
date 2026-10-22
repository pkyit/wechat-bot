package com.example.wechat.springboot.client;

import com.example.wechat.core.exception.WeChatException;
import com.example.wechat.core.model.MarkdownMessage;
import com.example.wechat.core.model.TextMessage;
import com.example.wechat.core.model.TokenResponse;
import com.example.wechat.core.model.WeChatMessage;
import com.example.wechat.core.model.WeChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 企业微信自建应用客户端（Spring Boot 版）.
 *
 * <p>支持按 userid 推送给指定成员。access_token 自动缓存，过期自动刷新重试。
 *
 * <p>线程安全，单例复用。token 刷新过程加锁，避免并发刷新导致令牌互相失效。
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
    private final Object tokenLock = new Object();

    public WeChatAppBotClient(String corpId, String agentId, String secret, RestClient restClient) {
        this(corpId, parseAgentId(agentId), secret, restClient);
    }

    public WeChatAppBotClient(String corpId, int agentId, String secret, RestClient restClient) {
        if (!StringUtils.hasText(corpId)) {
            throw new IllegalArgumentException("corpId 不能为空");
        }
        if (!StringUtils.hasText(secret)) {
            throw new IllegalArgumentException("secret 不能为空");
        }
        if (agentId <= 0) {
            throw new IllegalArgumentException("agentId 必须是正整数");
        }
        if (restClient == null) {
            throw new IllegalArgumentException("restClient 不能为空");
        }
        this.corpId = corpId;
        this.agentId = agentId;
        this.secret = secret;
        this.restClient = restClient;
    }

    private static int parseAgentId(String agentId) {
        if (!StringUtils.hasText(agentId)) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        try {
            return Integer.parseInt(agentId.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("agentId 必须是数字，当前值: " + agentId, e);
        }
    }

    /**
     * 发送消息给指定用户.
     *
     * @param toUser 接收人 userid，多个用 {@code |} 分隔；{@code "@all"} 表示全员
     */
    public WeChatResponse send(String toUser, WeChatMessage message) {
        if (!StringUtils.hasText(toUser)) {
            throw new IllegalArgumentException("toUser 不能为空");
        }
        if (message == null) {
            throw new IllegalArgumentException("message 不能为空");
        }
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

    private WeChatResponse doSend(Map<String, Object> body, boolean retrying) {
        CachedToken cached = getAccessToken();
        String url = SEND_MSG_URL.formatted(cached.token());

        WeChatResponse response;
        try {
            response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(WeChatResponse.class);
        } catch (RestClientException e) {
            throw new WeChatException("请求自建应用接口失败: " + e.getMessage(), e);
        }

        if (response == null) {
            throw new WeChatException("自建应用返回空响应");
        }

        // token 失效：仅清除本次使用的 token，避免误清其他线程刚刷新的新 token
        if (response.tokenExpired() && !retrying) {
            log.warn("access_token 失效（errcode={}），刷新后重试", response.errcode());
            tokenCache.compareAndSet(cached, null);
            return doSend(body, true);
        }

        if (!response.success()) {
            throw new WeChatException(response.errcode(), response.errmsg());
        }

        log.info("自建应用消息发送成功, invalidUser={}", response.invalidUser());
        return response;
    }

    /**
     * 获取 access_token，带缓存.
     *
     * <p>双重检查 + 同步刷新：缓存有效直接返回；否则串行请求新 token 并写入缓存。
     */
    private CachedToken getAccessToken() {
        CachedToken cached = tokenCache.get();
        if (isValid(cached)) {
            return cached;
        }

        synchronized (tokenLock) {
            cached = tokenCache.get();
            if (isValid(cached)) {
                return cached;
            }

            TokenResponse result;
            try {
                result = restClient.get()
                        .uri(GET_TOKEN_URL.formatted(corpId, secret))
                        .retrieve()
                        .body(TokenResponse.class);
            } catch (RestClientException e) {
                throw new WeChatException("获取 access_token 失败: " + e.getMessage(), e);
            }

            if (result == null) {
                throw new WeChatException("获取 access_token 返回空响应");
            }

            String token = result.requireAccessToken();
            int expiresIn = result.expiresInOrDefault();
            long expireAt = Instant.now().getEpochSecond() + expiresIn;

            CachedToken fresh = new CachedToken(token, expireAt);
            tokenCache.set(fresh);
            log.debug("获取新 access_token 成功，有效期 {} 秒", expiresIn);
            return fresh;
        }
    }

    private static boolean isValid(CachedToken cached) {
        return cached != null
                && Instant.now().getEpochSecond() < cached.expireAt() - TOKEN_REFRESH_MARGIN_SECONDS;
    }

    private record CachedToken(String token, long expireAt) {
    }
}
