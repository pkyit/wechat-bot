package com.example.wechat.jdk17;

import com.example.wechat.core.exception.WeChatException;
import com.example.wechat.core.model.MarkdownMessage;
import com.example.wechat.core.model.TextMessage;
import com.example.wechat.core.model.TokenResponse;
import com.example.wechat.core.model.WeChatMessage;
import com.example.wechat.core.model.WeChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 企业微信自建应用 Bot.
 *
 * <p>通过自建应用可以将消息推送给<b>指定成员</b>（按 userid）、部门或标签，
 * 也可推送到全员。需要企业管理员在「应用管理」中创建自建应用并获取：
 * <ul>
 *   <li>corpId — 企业 ID（「我的企业」→ 企业信息）</li>
 *   <li>agentId — 应用 ID（正整数）</li>
 *   <li>secret — 应用密钥</li>
 * </ul>
 *
 * <p><b>注意：</b>接收人用的是成员 {@code userid}（通讯录中的账号），不是手机号或昵称。
 *
 * <p>线程安全，单例复用。access_token 有效期 7200s：内部缓存并提前 5 分钟刷新；
 * 刷新过程加锁，避免并发刷新导致令牌互相失效；接口返回 token 失效时自动重试一次。
 */
public final class WeChatAppBot {

    private static final Logger log = LoggerFactory.getLogger(WeChatAppBot.class);

    private static final String DEFAULT_BASE_URL = "https://qyapi.weixin.qq.com";
    private static final String GET_TOKEN_PATH = "/cgi-bin/gettoken?corpid=%s&corpsecret=%s";
    private static final String SEND_MSG_PATH = "/cgi-bin/message/send?access_token=%s";

    /** token 提前刷新的安全余量（秒） */
    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 300;

    private final String corpId;
    private final int agentId;
    private final String secret;
    private final WeChatHttpClient httpClient;
    private final String getTokenUrl;
    private final String sendMsgUrl;

    /** token 缓存：不可变值对象，通过 CAS 更新保证线程安全 */
    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>(null);
    /** 串行化 token 刷新，避免并发请求重复获取 token 导致旧 token 失效 */
    private final Object tokenLock = new Object();

    public WeChatAppBot(String corpId, String agentId, String secret) {
        this(corpId, parseAgentId(agentId), secret, new WeChatHttpClient());
    }

    public WeChatAppBot(String corpId, int agentId, String secret) {
        this(corpId, agentId, secret, new WeChatHttpClient());
    }

    public WeChatAppBot(String corpId, int agentId, String secret, WeChatHttpClient httpClient) {
        this(corpId, agentId, secret, httpClient, DEFAULT_BASE_URL);
    }

    /**
     * 允许自定义 API 基址（便于通过网关访问或做本地集成测试）.
     */
    WeChatAppBot(String corpId, int agentId, String secret, WeChatHttpClient httpClient, String baseUrl) {
        if (corpId == null || corpId.isBlank()) {
            throw new IllegalArgumentException("corpId 不能为空");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret 不能为空");
        }
        if (agentId <= 0) {
            throw new IllegalArgumentException("agentId 必须是正整数");
        }
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient 不能为空");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl 不能为空");
        }
        this.corpId = corpId;
        this.agentId = agentId;
        this.secret = secret;
        this.httpClient = httpClient;
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.getTokenUrl = normalizedBaseUrl + GET_TOKEN_PATH;
        this.sendMsgUrl = normalizedBaseUrl + SEND_MSG_PATH;
    }

    private static int parseAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
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
     * @param toUser  接收人 userid，多个用 {@code |} 分隔；{@code "@all"} 表示全员
     * @param message 消息
     * @return 是否成功（失败会抛出 {@link WeChatException}，返回 false 的情况不会出现）
     */
    public boolean send(String toUser, WeChatMessage message) {
        if (toUser == null || toUser.isBlank()) {
            throw new IllegalArgumentException("toUser 不能为空");
        }
        Map<String, Object> body = buildMessageBody(message);
        body.put("touser", toUser);
        return doSend(body, false);
    }

    /**
     * 快捷发送文本给指定用户.
     */
    public boolean sendText(String toUser, String content) {
        return send(toUser, TextMessage.of(content));
    }

    /**
     * 快捷发送 Markdown 给指定用户.
     */
    public boolean sendMarkdown(String toUser, String content) {
        return send(toUser, MarkdownMessage.of(content));
    }

    /**
     * 发送消息到部门.
     *
     * @param toParty 部门 ID，多个用 {@code |} 分隔
     */
    public boolean sendToParty(String toParty, WeChatMessage message) {
        if (toParty == null || toParty.isBlank()) {
            throw new IllegalArgumentException("toParty 不能为空");
        }
        Map<String, Object> body = buildMessageBody(message);
        body.put("toparty", toParty);
        return doSend(body, false);
    }

    private Map<String, Object> buildMessageBody(WeChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message 不能为空");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", message.msgType().code());
        body.put("agentid", agentId);
        body.put(message.msgType().code(), message.body());
        return body;
    }

    private boolean doSend(Map<String, Object> body, boolean retrying) {
        CachedToken cached = getAccessToken();
        String url = sendMsgUrl.formatted(cached.token());

        WeChatResponse response = httpClient.post(url, body);

        // token 失效：仅清除本次使用的 token，避免误清其他线程刚刷新的新 token
        if (response.tokenExpired() && !retrying) {
            log.warn("access_token 失效（errcode={}），刷新后重试", response.errcode());
            tokenCache.compareAndSet(cached, null);
            return doSend(body, true);
        }

        if (response.success()) {
            log.info("自建应用消息发送成功, invalidUser={}", response.invalidUser());
            return true;
        }

        log.error("自建应用消息发送失败: errcode={}, errmsg={}", response.errcode(), response.errmsg());
        throw new WeChatException(response.errcode(), response.errmsg());
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

            TokenResponse result = httpClient.get(
                    getTokenUrl.formatted(corpId, secret), TokenResponse.class);
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

    /**
     * 不可变的 token 缓存值对象.
     */
    private record CachedToken(String token, long expireAt) {
    }
}
