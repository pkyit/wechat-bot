package com.example.wechat.jdk17;

import com.example.wechat.core.exception.WeChatException;
import com.example.wechat.core.model.MarkdownMessage;
import com.example.wechat.core.model.TextMessage;
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
 *   <li>agentId — 应用 ID</li>
 *   <li>secret — 应用密钥</li>
 * </ul>
 *
 * <p><b>注意：</b>接收人用的是成员 {@code userid}（通讯录中的账号），不是手机号或昵称。
 *
 * <p>线程安全，单例复用。access_token 有效期 7200s，内部自动缓存并在过期时刷新。
 */
public final class WeChatAppBot {

    private static final Logger log = LoggerFactory.getLogger(WeChatAppBot.class);

    private static final String GET_TOKEN_URL =
            "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s";
    private static final String SEND_MSG_URL =
            "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=%s";

    /** token 提前刷新的安全余量（秒） */
    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 300;

    private final String corpId;
    private final int agentId;
    private final String secret;
    private final WeChatHttpClient httpClient;

    /** token 缓存：不可变值对象，通过 CAS 更新保证线程安全 */
    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>(null);

    public WeChatAppBot(String corpId, String agentId, String secret) {
        this(corpId, Integer.parseInt(agentId), secret, new WeChatHttpClient());
    }

    public WeChatAppBot(String corpId, int agentId, String secret, WeChatHttpClient httpClient) {
        if (corpId == null || corpId.isBlank()) {
            throw new IllegalArgumentException("corpId 不能为空");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret 不能为空");
        }
        this.corpId = corpId;
        this.agentId = agentId;
        this.secret = secret;
        this.httpClient = httpClient;
    }

    /**
     * 发送消息给指定用户.
     *
     * @param toUser  接收人 userid，多个用 {@code |} 分隔；{@code "@all"} 表示全员
     * @param message 消息
     * @return 是否成功
     */
    public boolean send(String toUser, WeChatMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", toUser);
        body.put("msgtype", message.msgType().code());
        body.put("agentid", agentId);
        body.put(message.msgType().code(), message.body());

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
        Map<String, Object> body = buildMessageBody(message);
        body.put("toparty", toParty);
        return doSend(body, false);
    }

    private Map<String, Object> buildMessageBody(WeChatMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", message.msgType().code());
        body.put("agentid", agentId);
        body.put(message.msgType().code(), message.body());
        return body;
    }

    private boolean doSend(Map<String, Object> body, boolean retrying) {
        String token = getAccessToken();
        String url = SEND_MSG_URL.formatted(token);

        WeChatResponse response = httpClient.post(url, body);

        // token 失效，清缓存后重试一次
        if (response.tokenExpired() && !retrying) {
            log.warn("access_token 失效，刷新后重试");
            tokenCache.set(null);
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
     * 双重检查：先读缓存 → 未过期直接返回 → 过期则请求新 token 并 CAS 更新.
     */
    private String getAccessToken() {
        CachedToken cached = tokenCache.get();
        long now = Instant.now().getEpochSecond();

        if (cached != null && now < cached.expireAt() - TOKEN_REFRESH_MARGIN_SECONDS) {
            return cached.token();
        }

        // token 过期或不存在，请求新的
        Map<String, Object> result = httpClient.get(GET_TOKEN_URL.formatted(corpId, secret));
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

    /**
     * 不可变的 token 缓存值对象.
     */
    private record CachedToken(String token, long expireAt) {
    }
}
