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
 * <p>运行前请将下方常量替换为你自己的配置。
 */
public final class DemoMain {

    // ========== 群机器人配置 ==========
    private static final String GROUP_WEBHOOK =
            "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=替换为你的key";

    // ========== 自建应用配置 ==========
    private static final String CORP_ID = "替换为你的企业ID";
    private static final String AGENT_ID = "替换为你的应用AgentId";
    private static final String SECRET = "替换为你的应用Secret";
    private static final String TO_USER = "替换为接收人userid";

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
        try {
            WeChatAppBot appBot = new WeChatAppBot(CORP_ID, AGENT_ID, SECRET);

            // 发给指定人
            appBot.sendText(TO_USER, "【告警待处理】订单服务出现异常，请尽快处理");

            // 发给多个人（用 | 分隔）
            appBot.sendMarkdown("zhangsan|lisi", """
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
}
