package com.example.wechat.jdk17;

import com.example.wechat.core.model.TextMessage;

/**
 * JDK17 原生版 Demo 入口.
 *
 * <p>演示两种企业微信告警接入方式：
 * <ol>
 *   <li>群机器人（Webhook）— 最简，发到群</li>
 *   <li>自建应用 — 可发给指定人</li>
 * </ol>
 *
 * <p>运行前通过环境变量或 JVM 系统属性提供配置，未配置的通道会自动跳过：
 * <pre>
 * export WECHAT_GROUP_WEBHOOK="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"
 * export WECHAT_CORP_ID="wwxxxxxxxxxxxx"
 * export WECHAT_AGENT_ID="1000002"
 * export WECHAT_APP_SECRET="xxxxxxxx"
 * export WECHAT_TO_USER="zhangsan"
 * mvn exec:java
 * </pre>
 *
 * <p>也可以使用 {@code -DWECHAT_GROUP_WEBHOOK=...} 形式的系统属性。
 */
public final class DemoMain {

    // ========== 群机器人配置 ==========
    private static final String GROUP_WEBHOOK = setting("WECHAT_GROUP_WEBHOOK");

    // ========== 自建应用配置 ==========
    private static final String CORP_ID = setting("WECHAT_CORP_ID");
    private static final String AGENT_ID = setting("WECHAT_AGENT_ID");
    private static final String SECRET = setting("WECHAT_APP_SECRET");
    private static final String TO_USER = setting("WECHAT_TO_USER");

    private DemoMain() {
    }

    public static void main(String[] args) {
        System.out.println("===== 企业微信告警 Demo (JDK17 原生版) =====\n");

        demoGroupBot();
        demoAppBot();

        System.out.println("\n===== Demo 执行完毕 =====");
    }

    /**
     * 演示一：群机器人（Webhook）
     */
    private static void demoGroupBot() {
        System.out.println("【1】群机器人发送...");
        if (!configured(GROUP_WEBHOOK)) {
            System.out.println("    ⏭ 已跳过：未配置 WECHAT_GROUP_WEBHOOK");
            return;
        }
        try {
            WeChatGroupBot groupBot = new WeChatGroupBot(GROUP_WEBHOOK);

            // 发送文本并 @ 指定人
            groupBot.send(TextMessage.builder()
                    .content("【告警】CPU 使用率超过 90%，请关注！")
                    .mentionMobiles("13800138000")
                    .build());

            // 发送 Markdown（告警推荐格式）
            groupBot.sendMarkdown("""
                    ## <font color="warning">⚠️ 告警通知</font>
                    > **服务：** order-service
                    > **级别：** P0
                    > **时间：** %s
                    > **内容：** 数据库连接池耗尽，当前活跃连接 100/100
                    > **主机：** 10.0.0.12
                    """.formatted(java.time.LocalDateTime.now()));

            System.out.println("    ✅ 群机器人消息发送成功");
        } catch (Exception e) {
            System.out.println("    ❌ 群机器人发送失败: " + e.getMessage());
        }
    }

    /**
     * 演示二：自建应用（发给指定人）
     */
    private static void demoAppBot() {
        System.out.println("\n【2】自建应用发送给指定人...");
        if (!configured(CORP_ID) || !configured(AGENT_ID) || !configured(SECRET) || !configured(TO_USER)) {
            System.out.println("    ⏭ 已跳过：请配置 WECHAT_CORP_ID / WECHAT_AGENT_ID / WECHAT_APP_SECRET / WECHAT_TO_USER");
            return;
        }
        try {
            WeChatAppBot appBot = new WeChatAppBot(CORP_ID, AGENT_ID, SECRET);

            // 发给指定人
            appBot.sendText(TO_USER, "【告警待处理】订单服务出现异常，请尽快处理");

            // 发给多个人（用 | 分隔）
            appBot.sendMarkdown(TO_USER, """
                    ## 系统告警
                    - 服务：payment-service
                    - 错误：支付回调超时
                    - 持续时间：5 分钟
                    """);

            // 发给全员
            // appBot.sendText("@all", "【通知】系统将于今晚 23:00 进行维护");

            System.out.println("    ✅ 自建应用消息发送成功");
        } catch (Exception e) {
            System.out.println("    ❌ 自建应用发送失败: " + e.getMessage());
        }
    }

    /** 优先读取 JVM 系统属性，其次读取环境变量. */
    private static String setting(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return value;
    }

    private static boolean configured(String value) {
        return value != null && !value.isBlank();
    }
}
