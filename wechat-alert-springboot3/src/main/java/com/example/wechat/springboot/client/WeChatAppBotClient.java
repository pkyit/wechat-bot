package com.example.wechat.springboot.client;

import com.example.wechat.core.exception.WeChatException;
import com.example.wechat.core.model.MarkdownMessage;
import com.example.wechat.core.model.TextMessage;
import com.example.wechat.core.model.WeChatMessage;
import com.example.wechat.core.model.WeChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
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

    /** token 刷新互斥锁：防止并发过期时多线程同时请求 gettoken 触发频率限制 */
    private final Object tokenRefreshLock = new Object();

    public WeChatAppBotClient(String corpId, String agentId, String secret, RestClient restClient) {
        if (corpId == null || corpId.isBlank()) {
            throw new IllegalArgumentException("corpId 不能为空");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret 不能为空");
        }
        this.corpId = corpId;
        this.agentId = parseAgentId(agentId);
        this.secret = secret;
        this.restClient = restClient;
    }

    private static int parseAgentId(String agentId) {
        try {
            return Integer.parseInt(agentId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("agentId 必须是纯数字，请检查 wechat.alert.app.agent-id 配置，实际值: " + agentId, e);
        }
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

    private WeChatResponse doSend(Map<String, Object> body, boolean retrying) {
        String token = getAccessToken();
        String url = SEND_MSG_URL.formatted(token);

        WeChatResponse response;
        try {
            response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(WeChatResponse.class);
        } catch (RestClientException e) {
            // 4xx/5xx 或 IO 错误统一包装为 WeChatException，与 JDK 版异常契约一致
            throw new WeChatException("请求企业微信接口失败: " + e.getMessage(), e);
        }

        if (response == null) {
            throw new WeChatException("自建应用返回空响应");
        }

        // token 失效 → 清缓存重试一次
        if (response.tokenExpired() && !retrying) {
            log.warn("access_token 失效，刷新后重试");
            // CAS 清理：只清掉自己刚才使用的那个旧 token，
            // 避免误清其他线程刚刷新写入的新 token
            tokenCache.updateAndGet(c -> c != null && c.token().equals(token) ? null : c);
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
     * 先无锁读缓存；过期时在互斥锁内做双重检查后再请求，
     * 保证同一时刻只有一个线程调用 gettoken，避免并发刷新触发企业微信频率限制。
     */
    private String getAccessToken() {
        CachedToken cached = tokenCache.get();
        long now = Instant.now().getEpochSecond();

        if (isValid(cached, now)) {
            return cached.token();
        }

        synchronized (tokenRefreshLock) {
            // 锁内双重检查：排队期间可能已有线程刷新成功
            cached = tokenCache.get();
            now = Instant.now().getEpochSecond();
            if (isValid(cached, now)) {
                return cached.token();
            }

            String url = GET_TOKEN_URL.formatted(corpId, secret);
            Map<String, Object> result;
            try {
                result = restClient.get()
                        .uri(url)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
            } catch (RestClientException e) {
                throw new WeChatException("获取 access_token 失败: " + e.getMessage(), e);
            }

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
    }

    private static boolean isValid(CachedToken cached, long now) {
        return cached != null && now < cached.expireAt() - TOKEN_REFRESH_MARGIN_SECONDS;
    }

    private record CachedToken(String token, long expireAt) {
    }
}
