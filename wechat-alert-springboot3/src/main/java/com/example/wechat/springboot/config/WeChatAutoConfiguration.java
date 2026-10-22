package com.example.wechat.springboot.config;

import com.example.wechat.springboot.client.WeChatAppBotClient;
import com.example.wechat.springboot.client.WeChatGroupBotClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 企业微信告警自动配置.
 *
 * <p>根据 {@code wechat.alert.*} 配置条件装配：
 * <ul>
 *   <li>群机器人：{@code wechat.alert.group-bot.enabled=true}，且必须配置 webhook</li>
 *   <li>自建应用：{@code wechat.alert.app.enabled=true}，且必须配置 corp-id / agent-id / secret</li>
 * </ul>
 *
 * <p>启用但缺少必填配置时，应用启动阶段会抛出带有明确提示的
 * {@link IllegalStateException}，避免运行期才暴露错误。
 *
 * <p>使用 Spring 6.1+ 的 {@link RestClient} 替代 RestTemplate，并统一设置连接/读取超时，
 * 防止企业微信接口不可用时无限阻塞请求线程。
 */
@Configuration
@EnableConfigurationProperties(WeChatProperties.class)
public class WeChatAutoConfiguration {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 群机器人客户端.
     */
    @Bean
    @ConditionalOnProperty(prefix = "wechat.alert.group-bot", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public WeChatGroupBotClient weChatGroupBotClient(WeChatProperties properties,
                                                     RestClient.Builder restClientBuilder) {
        String webhook = properties.getGroupBot().getWebhook();
        if (!StringUtils.hasText(webhook)) {
            throw new IllegalStateException(
                    "群机器人已启用，但未配置 wechat.alert.group-bot.webhook（或环境变量 WECHAT_GROUP_WEBHOOK）");
        }
        return new WeChatGroupBotClient(webhook, buildRestClient(restClientBuilder));
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
        requireText(app.getCorpId(), "wechat.alert.app.corp-id（或环境变量 WECHAT_CORP_ID）");
        requireText(app.getAgentId(), "wechat.alert.app.agent-id（或环境变量 WECHAT_AGENT_ID）");
        requireText(app.getSecret(), "wechat.alert.app.secret（或环境变量 WECHAT_APP_SECRET）");
        return new WeChatAppBotClient(app.getCorpId(), app.getAgentId(), app.getSecret(),
                buildRestClient(restClientBuilder));
    }

    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("自建应用已启用，但未配置 " + name);
        }
    }

    private static RestClient buildRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return builder.requestFactory(requestFactory).build();
    }
}
