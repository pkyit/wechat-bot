package com.example.wechat.springboot.config;

import com.example.wechat.springboot.client.WeChatAppBotClient;
import com.example.wechat.springboot.client.WeChatGroupBotClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 企业微信告警自动配置.
 *
 * <p>根据 {@code wechat.alert.*} 配置条件装配：
 * <ul>
 *   <li>群机器人：{@code wechat.alert.group-bot.enabled=true}</li>
 *   <li>自建应用：{@code wechat.alert.app.enabled=true}</li>
 * </ul>
 *
 * <p>使用 Spring 6.1+ 的 {@link RestClient} 替代 RestTemplate，API 更现代流畅。
 * <p>{@code WeChatAlertService} 通过 {@code @Service} 自动装配，无需在此声明。
 */
@Configuration
@EnableConfigurationProperties(WeChatProperties.class)
public class WeChatAutoConfiguration {

    /**
     * 群机器人客户端.
     */
    @Bean
    @ConditionalOnProperty(prefix = "wechat.alert.group-bot", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public WeChatGroupBotClient weChatGroupBotClient(WeChatProperties properties,
                                                     RestClient.Builder restClientBuilder) {
        RestClient restClient = restClientBuilder.build();
        return new WeChatGroupBotClient(properties.getGroupBot().getWebhook(), restClient);
    }

    /**
     * 自建应用客户端.
     */
    @Bean
    @ConditionalOnProperty(prefix = "wechat.alert.app", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public WeChatAppBotClient weChatAppBotClient(WeChatProperties properties,
                                                 RestClient.Builder restClientBuilder) {
        RestClient restClient = restClientBuilder.build();
        WeChatProperties.App app = properties.getApp();
        return new WeChatAppBotClient(app.getCorpId(), app.getAgentId(), app.getSecret(), restClient);
    }
}
