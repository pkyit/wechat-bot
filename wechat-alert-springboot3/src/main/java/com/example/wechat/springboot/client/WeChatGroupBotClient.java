package com.example.wechat.springboot.client;

import com.example.wechat.core.exception.WeChatException;
import com.example.wechat.core.model.MarkdownMessage;
import com.example.wechat.core.model.TextMessage;
import com.example.wechat.core.model.WeChatMessage;
import com.example.wechat.core.model.WeChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
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
        if (!StringUtils.hasText(webhookUrl)) {
            throw new IllegalArgumentException("群机器人 webhook 不能为空");
        }
        if (restClient == null) {
            throw new IllegalArgumentException("restClient 不能为空");
        }
        this.webhookUrl = webhookUrl;
        this.restClient = restClient;
    }

    /**
     * 发送消息.
     *
     * @return 企业微信响应（errcode = 0）
     * @throws WeChatException 网络异常或接口返回非 0 错误码时抛出
     */
    public WeChatResponse send(WeChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message 不能为空");
        }
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
            throw new WeChatException("请求群机器人接口失败: " + e.getMessage(), e);
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
