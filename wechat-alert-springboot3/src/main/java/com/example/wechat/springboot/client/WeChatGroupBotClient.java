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
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业微信群机器人客户端（Spring Boot 版）.
 *
 * <p>基于 {@link RestClient} 实现，线程安全，单例复用。
 */
public class WeChatGroupBotClient {

    private static final Logger log = LoggerFactory.getLogger(WeChatGroupBotClient.class);

    private final String webhookUrl;
    private final RestClient restClient;

    public WeChatGroupBotClient(String webhookUrl, RestClient restClient) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException("webhookUrl 不能为空，请检查 wechat.alert.group-bot.webhook 配置");
        }
        this.webhookUrl = webhookUrl;
        this.restClient = restClient;
    }

    /**
     * 发送消息.
     */
    public WeChatResponse send(WeChatMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", message.msgType().code());
        body.put(message.msgType().code(), message.body());

        WeChatResponse response;
        try {
            response = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(WeChatResponse.class);
        } catch (RestClientException e) {
            // 4xx/5xx 或 IO 错误统一包装为 WeChatException，与 JDK 版异常契约一致
            throw new WeChatException("请求企业微信接口失败: " + e.getMessage(), e);
        }

        if (response == null) {
            throw new WeChatException("群机器人返回空响应");
        }
        if (!response.success()) {
            throw new WeChatException(response.errcode(), response.errmsg());
        }
        log.info("群机器人消息发送成功");
        return response;
    }

    public WeChatResponse sendText(String content) {
        return send(TextMessage.of(content));
    }

    public WeChatResponse sendMarkdown(String content) {
        return send(MarkdownMessage.of(content));
    }
}
