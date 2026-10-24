package com.example.wechat.springboot.config;

import com.example.wechat.springboot.client.WeChatAppBotClient;
import com.example.wechat.springboot.client.WeChatGroupBotClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

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
        return new WeChatGroupBotClient(properties.getGroupBot().getWebhook(),
                buildRestClient(restClientBuilder));
    }

    /**
     * 自建应用客户端.
     */
    @Bean
    @ConditionalOnProperty(prefix = "wechat.alert.app", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public WeChatAppBotClient weChatAppBotClient(WeChatProperties properties,
                                                 RestClient.Builder restClientBuilder) {
        WeChatProperties.App app = properties.getApp();
        return new WeChatAppBotClient(app.getCorpId(), app.getAgentId(), app.getSecret(),
                buildRestClient(restClientBuilder));
    }

    /**
     * 构建带超时保护的 RestClient.
     *
     * <p>默认的 SimpleClientHttpRequestFactory 不设置任何超时，
     * 企业微信接口网络故障时调用线程会永久挂起，必须显式配置。
     */
    private static RestClient buildRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return builder.requestFactory(factory).build();
    }
}
