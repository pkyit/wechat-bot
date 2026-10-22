package com.example.wechat.springboot.service;

import com.example.wechat.core.exception.WeChatException;
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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

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
     *
     * @throws IllegalStateException 群机器人通道未启用
     */
    public WeChatResponse alertToGroup(String content) {
        requireText(content, "content");
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
     * @throws IllegalStateException 自建应用通道未启用
     */
    public WeChatResponse alertToUser(String toUser, String content) {
        requireText(toUser, "toUser");
        requireText(content, "content");
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
     * 单个通道失败不会影响另一个通道；如果所有已启用通道都失败，则抛出异常，
     * 避免调用方误以为告警已送达。
     *
     * @return 各通道发送结果
     * @throws IllegalStateException 没有任何可用通道
     * @throws WeChatException       所有通道均发送失败
     */
    public AlertOutcome alert(String title, String content) {
        requireText(title, "title");
        requireText(content, "content");

        WeChatGroupBotClient groupClient = groupBotProvider.getIfAvailable();
        WeChatAppBotClient appClient = appBotProvider.getIfAvailable();
        String defaultOwner = properties.getApp().getDefaultOwner();
        boolean appChannelEnabled = appClient != null && StringUtils.hasText(defaultOwner);

        if (groupClient == null && !appChannelEnabled) {
            throw new IllegalStateException(
                    "未启用任何告警通道：请启用 wechat.alert.group-bot 或 wechat.alert.app 并配置 default-owner");
        }

        // 先完成内容校验（超长等属于调用方参数错误，直接抛 IllegalArgumentException）
        MarkdownMessage markdown = MarkdownMessage.of("""
                ## <font color="warning">⚠️ %s</font>
                %s
                """.formatted(title, content));
        TextMessage ownerMessage = appChannelEnabled
                ? TextMessage.of("【告警待处理】" + title + " - " + content)
                : null;

        boolean groupSent = false;
        boolean appSent = false;
        List<String> failures = new ArrayList<>();

        // 1. 群广播
        if (groupClient != null) {
            try {
                groupClient.send(markdown);
                groupSent = true;
            } catch (Exception e) {
                log.error("群广播失败", e);
                failures.add("群机器人: " + e.getMessage());
            }
        }

        // 2. 通知默认负责人
        if (appChannelEnabled) {
            try {
                appClient.send(defaultOwner, ownerMessage);
                appSent = true;
            } catch (Exception e) {
                log.error("私信通知失败", e);
                failures.add("自建应用: " + e.getMessage());
            }
        }

        if (!groupSent && !appSent) {
            throw new WeChatException("所有告警通道均发送失败: " + String.join("; ", failures));
        }
        if (!failures.isEmpty()) {
            log.warn("部分告警通道发送失败: {}", failures);
        }
        return new AlertOutcome(groupSent, appSent, List.copyOf(failures));
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }

    /**
     * 全通道告警结果.
     *
     * @param groupSent 群机器人是否发送成功
     * @param appSent   自建应用是否发送成功
     * @param failures  失败通道及原因
     */
    public record AlertOutcome(boolean groupSent, boolean appSent, List<String> failures) {
    }
}
