# 企业微信告警接入 Demo

企业微信告警通知的两种接入方式完整示例，包含 **JDK17 原生版** 和 **Spring Boot 3.5 版**。

## 两种接入方式

| 方式 | 企业微信群机器人（Webhook） | 企业微信自建应用 |
|------|---------------------------|-----------------|
| **接收对象** | 只能发到**群** | 可发给**指定人**（userid）、部门、全员 |
| **接入复杂度** | 极低，一个 webhook URL | 需 corpId + agentId + secret + access_token |
| **频率限制** | 20 条/分钟 | 接口调用有限制，需缓存 token |
| **适用场景** | 简单群告警 | 需要 @ 具体负责人、精确推送 |

## 项目结构

```
wechat-alert-demo/
├── wechat-alert-core/            # 核心模型（消息体、响应、枚举、异常）
├── wechat-alert-jdk17/           # JDK17 原生版（java.net.http.HttpClient）
└── wechat-alert-springboot3/     # Spring Boot 3.5 版（自动配置 + RestClient）
```

## 快速开始

### 方式一：群机器人（Webhook）

**1. 获取 Webhook URL**

企业微信群 → 右上角「…」→「群机器人」→「添加机器人」→ 复制 Webhook 地址。

**2. JDK17 原生版**

```java
WeChatGroupBot bot = new WeChatGroupBot("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=你的key");

// 文本消息 + @ 指定人
bot.send(TextMessage.builder()
        .content("【告警】CPU 使用率超过 90%")
        .mentionMobiles("13800138000")
        .build());

// Markdown 消息（告警推荐）
bot.sendMarkdown("""
        ## <font color="warning">⚠️ 告警通知</font>
        > **服务：** order-service
        > **级别：** P0
        """);
```

**3. Spring Boot 3.5 版**

```yaml
wechat:
  alert:
    group-bot:
      enabled: true
      webhook: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=你的key
```

```java
@Autowired
private WeChatGroupBotClient groupBotClient;

groupBotClient.sendMarkdown("## 告警\n服务异常");
```

### 方式二：自建应用（发给指定人）

**1. 创建自建应用并获取参数**

| 参数 | 说明 | 在哪获取 |
|------|------|---------|
| `corpId` | 企业 ID | 管理后台 →「我的企业」→ 企业信息 |
| `agentId` | 应用 ID | 管理后台 →「应用管理」→ 自建应用 |
| `secret` | 应用密钥 | 应用详情页 |
| `userid` | 接收人账号 | 管理后台 →「通讯录」→ 成员详情的「账号」 |

**2. JDK17 原生版**

```java
WeChatAppBot bot = new WeChatAppBot("corpId", "agentId", "secret");

// 发给指定人
bot.sendText("zhangsan", "【告警】订单服务异常");

// 发给多个人
bot.sendMarkdown("zhangsan|lisi", "## 告警\n支付回调超时");

// 发给全员
bot.sendText("@all", "系统维护通知");
```

**3. Spring Boot 3.5 版**

```yaml
wechat:
  alert:
    app:
      enabled: true
      corp-id: 你的企业ID
      agent-id: "你的应用AgentId"
      secret: 你的应用Secret
      default-owner: 默认告警接收人userid
```

```java
@Autowired
private WeChatAppBotClient appBotClient;

appBotClient.sendText("zhangsan", "告警内容");
```

### 统一告警服务（Spring Boot）

```java
@Autowired
private WeChatAlertService alertService;

// 群广播 + 私信负责人
alertService.alert("数据库告警", "连接池耗尽");

// 仅发群
alertService.alertToGroup("告警内容");

// 仅发给指定人
alertService.alertToUser("zhangsan", "告警内容");
```

### REST API（Spring Boot）

启动后可通过 HTTP 接口调用：

```bash
# 发群消息
curl -X POST http://localhost:8080/api/wechat/group \
  -H "Content-Type: application/json" \
  -d '{"content":"告警内容"}'

# 发给指定人
curl -X POST "http://localhost:8080/api/wechat/user?toUser=zhangsan" \
  -H "Content-Type: application/json" \
  -d '{"content":"告警内容"}'

# 全通道告警
curl -X POST http://localhost:8080/api/wechat/alert \
  -H "Content-Type: application/json" \
  -d '{"title":"告警标题","content":"告警详情"}'
```

## 构建运行

```bash
# 编译全部模块
mvn clean package

# 运行 JDK17 原生版 Demo
cd wechat-alert-jdk17
mvn exec:java

# 运行 Spring Boot 版
cd wechat-alert-springboot3
mvn spring-boot:run
```

## 核心设计

- **消息模型**：`WeChatMessage` 接口 + `TextMessage` / `MarkdownMessage` record，不可变且线程安全
- **token 缓存**：自建应用的 access_token 用 `AtomicReference` 缓存，提前 5 分钟刷新，失效自动重试
- **条件装配**：Spring Boot 版通过 `@ConditionalOnProperty` 按需启用群机器人 / 自建应用
- **统一异常**：`WeChatException` 封装接口错误码，全局异常处理器返回标准 JSON

## 技术栈

- JDK 17（record、text block、switch 表达式、HttpClient）
- Jackson 2.17（JSON 序列化）
- Spring Boot 3.5（RestClient、@ConfigurationProperties、自动配置）
