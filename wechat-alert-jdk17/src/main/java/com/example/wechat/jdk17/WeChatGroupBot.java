package com.example.wechat.jdk17;

import com.example.wechat.core.exception.WeChatException;
import com.example.wechat.core.model.MarkdownMessage;
import com.example.wechat.core.model.TextMessage;
import com.example.wechat.core.model.WeChatMessage;
import com.example.wechat.core.model.WeChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业微信群机器人.
 *
 * <p>最简单的接入方式：在群里添加机器人获得 Webhook URL，直接 POST JSON 即可。
 * 适用于群内广播告警，无需 access_token。
 *
 * <p><b>获取 Webhook URL：</b>企业微信群 → 右上角「…」→「群机器人」→「添加机器人」→ 复制地址。
 *
 * <p>线程安全，单例复用即可。
 */
public final class WeChatGroupBot {

    private static final Logger log = LoggerFactory.getLogger(WeChatGroupBot.class);

    private final String webhookUrl;
    private final WeChatHttpClient httpClient;

    public WeChatGroupBot(String webhookUrl) {
        this(webhookUrl, new WeChatHttpClient());
    }

    public WeChatGroupBot(String webhookUrl, WeChatHttpClient httpClient) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException("webhookUrl 不能为空");
        }
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient 不能为空");
        }
        this.webhookUrl = webhookUrl;
        this.httpClient = httpClient;
    }

    /**
     * 发送任意类型消息.
     *
     * @param message 消息（TextMessage / MarkdownMessage）
     * @return 是否成功（失败会抛出 {@link WeChatException}，返回 false 的情况不会出现）
     */
    public boolean send(WeChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message 不能为空");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", message.msgType().code());
        body.put(message.msgType().code(), message.body());

        WeChatResponse response = httpClient.post(webhookUrl, body);
        if (response.success()) {
            log.info("群机器人发送成功");
            return true;
        }
        log.error("群机器人发送失败: errcode={}, errmsg={}", response.errcode(), response.errmsg());
        throw new WeChatException(response.errcode(), response.errmsg());
    }

    /**
     * 快捷发送文本消息.
     */
    public boolean sendText(String content) {
        return send(TextMessage.of(content));
    }

    /**
     * 快捷发送文本消息并 @ 指定手机号.
     */
    public boolean sendText(String content, String... atMobiles) {
        return send(TextMessage.builder()
                .content(content)
                .mentionMobiles(atMobiles)
                .build());
    }

    /**
     * 快捷发送 Markdown 消息（告警推荐）.
     */
    public boolean sendMarkdown(String content) {
        return send(MarkdownMessage.of(content));
    }
}
