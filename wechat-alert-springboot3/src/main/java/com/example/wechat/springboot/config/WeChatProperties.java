package com.example.wechat.springboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 企业微信告警配置属性.
 *
 * <p>对应 application.yml 中的 {@code wechat.alert} 前缀：
 * <pre>
 * wechat:
 *   alert:
 *     group-bot:
 *       enabled: true
 *       webhook: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
 *     app:
 *       enabled: true
 *       corp-id: wwxxxxxxxx
 *       agent-id: "1000002"
 *       secret: xxxxxxxxxxxxxxxxxxxx
 * </pre>
 */
@ConfigurationProperties(prefix = "wechat.alert")
public class WeChatProperties {

    private GroupBot groupBot = new GroupBot();
    private App app = new App();

    public GroupBot getGroupBot() {
        return groupBot;
    }

    public void setGroupBot(GroupBot groupBot) {
        this.groupBot = groupBot;
    }

    public App getApp() {
        return app;
    }

    public void setApp(App app) {
        this.app = app;
    }

    /** 群机器人配置 */
    public static class GroupBot {
        private boolean enabled = false;
        private String webhook;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getWebhook() {
            return webhook;
        }

        public void setWebhook(String webhook) {
            this.webhook = webhook;
        }
    }

    /** 自建应用配置 */
    public static class App {
        private boolean enabled = false;
        private String corpId;
        private String agentId;
        private String secret;
        /** 默认告警接收人 userid（全通道告警时私信通知此人） */
        private String defaultOwner;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCorpId() {
            return corpId;
        }

        public void setCorpId(String corpId) {
            this.corpId = corpId;
        }

        public String getAgentId() {
            return agentId;
        }

        public void setAgentId(String agentId) {
            this.agentId = agentId;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getDefaultOwner() {
            return defaultOwner;
        }

        public void setDefaultOwner(String defaultOwner) {
            this.defaultOwner = defaultOwner;
        }
    }
}
