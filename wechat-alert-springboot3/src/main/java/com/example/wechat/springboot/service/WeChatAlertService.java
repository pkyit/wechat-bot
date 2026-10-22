package com.example.wechat.springboot.service;

import com.example.wechat.core.model.MarkdownMessage;
import com.example.wechat.core.model.TextMessage;
import com.example.wechat.core.model.WeChatResponse;
import com.example.wechat.springboot.client.WeChatAppBotClient;
import com.example.wechat.springboot.client.WeChatGroupBotClient;
import com.example.wechat.springboot.config.WeChatProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 企业微信告警统一服务.
 *
 * <p>聚合群机器人与自建应用两种通道，提供一站式告警能力：
 * <ul>
 *   <li>{@link #alertToGroup} — 仅发群</li>
 *   <li>{@link #alertToUser} — 仅发给指定人</li>
 *   <li>{@link #alert} — 群广播 + 指定人通知（双管齐下）</li>
 * </ul>
 *
 * <p>通过 {@link ObjectProvider} 注入，未启用的通道不会报错。
 */
@Service
public class WeChatAlertService {

    private static final Logger log = LoggerFactory.getLogger(WeChatAlertService.class);

    private final WeChatProperties properties;
    private final ObjectProvider<WeChatGroupBotClient> groupBotProvider;
    private final ObjectProvider<WeChatAppBotClient> appBotProvider;

    public WeChatAlertService(WeChatProperties properties,
                              ObjectProvider<WeChatGroupBotClient> groupBotProvider,
                              ObjectProvider<WeChatAppBotClient> appBotProvider) {
        this.properties = properties;
        this.groupBotProvider = groupBotProvider;
        this.appBotProvider = appBotProvider;
    }

    /**
     * 告警推送到群.
     */
    public WeChatResponse alertToGroup(String content) {
        WeChatGroupBotClient client = groupBotProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("群机器人未启用，请配置 wechat.alert.group-bot.enabled=true");
        }
        return client.sendMarkdown(content);
    }

    /**
     * 告警推送给指定人.
     *
     * @param toUser  接收人 userid
     * @param content 消息内容
     */
    public WeChatResponse alertToUser(String toUser, String content) {
        WeChatAppBotClient client = appBotProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("自建应用未启用，请配置 wechat.alert.app.enabled=true");
        }
        return client.sendText(toUser, content);
    }

    /**
     * 全通道告警：群广播 + 通知配置的默认负责人.
     *
     * <p>如果配置了 {@code wechat.alert.app.default-owner}，会同时给该负责人发一条私信。
     */
    public void alert(String title, String content) {
        String markdown = """
                ## <font color="warning">⚠️ %s</font>
                %s
                """.formatted(title, content);

        // 1. 群广播（如果启用）
        WeChatGroupBotClient groupClient = groupBotProvider.getIfAvailable();
        if (groupClient != null) {
            try {
                groupClient.send(MarkdownMessage.of(markdown));
            } catch (Exception e) {
                log.error("群广播失败", e);
            }
        }

        // 2. 通知默认负责人（如果启用了自建应用且配置了负责人）
        WeChatAppBotClient appClient = appBotProvider.getIfAvailable();
        String defaultOwner = properties.getApp().getDefaultOwner();
        if (appClient != null && defaultOwner != null && !defaultOwner.isBlank()) {
            try {
                appClient.send(defaultOwner, TextMessage.of("【告警待处理】" + title + " - " + content));
            } catch (Exception e) {
                log.error("私信通知失败", e);
            }
        }
    }
}
