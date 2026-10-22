# 企业微信告警接入 Demo

企业微信告警通知的两种接入方式完整示例，包含 **JDK17 原生版** 和 **Spring Boot 3.5 版**。
核心模型与 HTTP 细节沉在 `wechat-alert-core`，两种运行时共享同一套消息模型与错误处理。

- 群机器人（Webhook）：一个 URL 发到群，零鉴权成本
- 自建应用：按 userid / 部门 / 全员精准推送，自动管理 `access_token`

---

## 目录

- [功能特性](#功能特性)
- [两种接入方式对比](#两种接入方式对比)
- [模块与代码结构](#模块与代码结构)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置参考](#配置参考)
- [REST API](#rest-api)
- [核心机制](#核心机制)
- [错误码与排查](#错误码与排查)
- [构建与测试](#构建与测试)
- [安全建议](#安全建议)
- [常见问题](#常见问题)
- [变更记录](#变更记录本次改进)
- [已知限制](#已知限制)

---

## 功能特性

- **两种通道**：群机器人 Webhook、自建应用（指定人 / 部门 / 全员）
- **统一消息模型**：`TextMessage`、`MarkdownMessage` 不可变 record，发送前校验长度
- **token 自动管理**：缓存、提前 5 分钟刷新、失效自动重试一次、并发刷新加锁
- **错误统一封装**：网络异常、HTTP 非 2xx、企业微信 `errcode != 0` 均抛出 `WeChatException`
- **Spring Boot 自动配置**：按配置条件装配、启动期校验必填项、统一超时
- **REST API**：可直接调用，也可作为告警网关给其他系统使用
- **可测试**：消息序列化、token 缓存/重试、HTTP 请求体、接口错误码均有测试覆盖

## 两种接入方式对比

| 对比项 | 企业微信群机器人（Webhook） | 企业微信自建应用 |
|--------|---------------------------|-----------------|
| **接收对象** | 只能发到**群** | 可发给**指定人**（userid）、部门、标签、全员 |
| **接入复杂度** | 极低，一个 webhook URL | 需 corpId + agentId + secret + access_token |
| **鉴权** | URL 中的 key 即凭证 | 先换取 `access_token`，有效期 7200s |
| **频率限制** | 20 条/分钟/机器人 | 受企业微信接口频率限制 |
| **适用场景** | 简单群告警、值班群广播 | 需要 @ 具体负责人、精确推送 |

## 模块与代码结构

```
wechat-alert-demo/
├── wechat-alert-core/                  # 核心模型（无 Spring 依赖）
│   └── model/
│       ├── WeChatMessage.java          # 消息抽象：msgType + body
│       ├── TextMessage.java            # 文本消息（支持 @userid / @手机号 / @all）
│       ├── MarkdownMessage.java        # Markdown 消息（告警推荐）
│       ├── WeChatResponse.java         # 通用响应 + token 失效错误码判断
│       ├── TokenResponse.java          # gettoken 响应（空值安全）
│       └── MsgType / WeChatException
│
├── wechat-alert-jdk17/                 # JDK17 原生版（java.net.http.HttpClient）
│   ├── WeChatHttpClient.java           # 底层 HTTP + JSON + 错误转换
│   ├── WeChatGroupBot.java             # 群机器人
│   ├── WeChatAppBot.java               # 自建应用（token 缓存）
│   └── DemoMain.java                   # 可执行 Demo（读取环境变量）
│
└── wechat-alert-springboot3/           # Spring Boot 3.5 版（RestClient）
    ├── config/WeChatProperties.java    # wechat.alert.* 配置
    ├── config/WeChatAutoConfiguration.java
    ├── client/WeChatGroupBotClient.java
    ├── client/WeChatAppBotClient.java
    ├── service/WeChatAlertService.java # 群 + 私信聚合，部分失败可感知
    ├── controller/WeChatAlertController.java
    └── controller/GlobalExceptionHandler.java
```

## 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17+（编译目标 17） |
| Maven | 3.9+ |
| Spring Boot | 3.5.x（仅 Spring 模块） |
| 企业微信 | 管理员权限（创建群机器人 / 自建应用） |

## 快速开始

### 1. 构建并安装到本地仓库

```bash
mvn clean install
```

> `install` 会把 `wechat-alert-core` 安装到本地仓库，后续单独运行 JDK17 版或 Spring Boot 版时才能解析到它。

### 2. 群机器人（Webhook）

企业微信群 → 右上角「…」→「群机器人」→「添加机器人」→ 复制 Webhook 地址。

```java
WeChatGroupBot bot = new WeChatGroupBot("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=你的key");

// 文本 + @ 手机号
bot.send(TextMessage.builder()
        .content("【告警】CPU 使用率超过 90%")
        .mentionMobiles("13800138000")
        .build());

// Markdown（告警推荐）
bot.sendMarkdown("""
        ## <font color="warning">⚠️ 告警通知</font>
        > **服务：** order-service
        > **级别：** P0
        """);
```

### 3. 自建应用（发给指定人）

| 参数 | 说明 | 获取位置 |
|------|------|---------|
| `corpId` | 企业 ID | 管理后台 →「我的企业」→ 企业信息 |
| `agentId` | 应用 ID（数字） | 管理后台 →「应用管理」→ 自建应用 |
| `secret` | 应用密钥 | 应用详情页 |
| `userid` | 接收人账号 | 管理后台 →「通讯录」→ 成员详情的「账号」 |

```java
WeChatAppBot bot = new WeChatAppBot("corpId", "1000002", "secret");

bot.sendText("zhangsan", "【告警】订单服务异常");
bot.sendMarkdown("zhangsan|lisi", "## 告警\n支付回调超时"); // 多个人用 | 分隔
bot.sendText("@all", "系统维护通知");                        // 全员
bot.sendToParty("1|2", TextMessage.of("部门告警"));           // 部门
```

### 4. 运行

**JDK17 原生版**（未配置的通道会自动跳过）：

```bash
cd wechat-alert-jdk17
export WECHAT_GROUP_WEBHOOK="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"
export WECHAT_CORP_ID="wwxxxxxxxxxxxx"
export WECHAT_AGENT_ID="1000002"
export WECHAT_APP_SECRET="xxxxxxxx"
export WECHAT_TO_USER="zhangsan"
mvn exec:java
```

也可以使用系统属性：`mvn exec:java -DWECHAT_GROUP_WEBHOOK=...`。

**Spring Boot 版**（默认两个通道关闭，未配置密钥也能启动）：

```bash
cd wechat-alert-springboot3

export WECHAT_ALERT_GROUP_BOT_ENABLED=true
export WECHAT_GROUP_WEBHOOK="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"

export WECHAT_ALERT_APP_ENABLED=true
export WECHAT_CORP_ID="wwxxxxxxxxxxxx"
export WECHAT_AGENT_ID="1000002"
export WECHAT_APP_SECRET="xxxxxxxx"
export WECHAT_DEFAULT_OWNER="zhangsan"

mvn spring-boot:run
```

启动后默认监听 `http://localhost:8080`。

## 配置参考

| 配置项 | 默认值 | 环境变量 | 说明 |
|--------|--------|---------|------|
| `wechat.alert.group-bot.enabled` | `false` | `WECHAT_ALERT_GROUP_BOT_ENABLED` | 是否启用群机器人 |
| `wechat.alert.group-bot.webhook` | 空 | `WECHAT_GROUP_WEBHOOK` | 群机器人 Webhook 完整地址 |
| `wechat.alert.app.enabled` | `false` | `WECHAT_ALERT_APP_ENABLED` | 是否启用自建应用 |
| `wechat.alert.app.corp-id` | 空 | `WECHAT_CORP_ID` | 企业 ID |
| `wechat.alert.app.agent-id` | 空 | `WECHAT_AGENT_ID` | 应用 AgentId，必须是数字 |
| `wechat.alert.app.secret` | 空 | `WECHAT_APP_SECRET` | 应用 Secret |
| `wechat.alert.app.default-owner` | 空 | `WECHAT_DEFAULT_OWNER` | 全通道告警时私信通知的 userid |

行为说明：

- 通道 `enabled=true` 但缺少必填配置时，**应用启动阶段直接失败**，并给出具体缺失项
- `default-owner` 为空时，`/api/wechat/alert` 只发送群消息
- 两个通道都未启用时，调用 `/api/wechat/alert` 返回 400，并提示未启用任何通道

## REST API

### 发送群消息

```bash
curl -X POST http://localhost:8080/api/wechat/group \
  -H "Content-Type: application/json" \
  -d '{"content":"## 告警\n数据库连接池耗尽"}'
```

```json
{"errcode":0,"errmsg":"ok","invalidUser":null}
```

### 发送给指定人

```bash
curl -X POST "http://localhost:8080/api/wechat/user?toUser=zhangsan" \
  -H "Content-Type: application/json" \
  -d '{"content":"【告警】订单服务异常"}'
```

### 全通道告警（群 + 默认负责人私信）

```bash
curl -X POST http://localhost:8080/api/wechat/alert \
  -H "Content-Type: application/json" \
  -d '{"title":"数据库告警","content":"连接池耗尽"}'
```

全部通道成功：

```json
{
  "status": "ok",
  "groupSent": true,
  "appSent": true,
  "failures": [],
  "message": "告警已发送"
}
```

部分通道失败（HTTP 仍为 200，调用方可据 `status` 判断）：

```json
{
  "status": "partial",
  "groupSent": true,
  "appSent": false,
  "failures": ["自建应用: 企业微信接口返回错误: errcode=81013, errmsg=user invalid"],
  "message": "告警已部分发送"
}
```

所有通道失败时返回 **502**；参数错误返回 **400**。错误结构：

```json
{
  "success": false,
  "message": "企业微信接口返回错误: errcode=93000, errmsg=invalid webhook url",
  "errcode": 93000
}
```

| HTTP 状态 | 场景 |
|-----------|------|
| 200 | 发送成功 / 部分通道成功 |
| 400 | 参数为空、JSON 格式错误、通道未启用、内容超长 |
| 404 | 接口不存在 |
| 502 | 企业微信接口报错或网络异常 |
| 500 | 未预期异常 |

## 核心机制

### 消息校验

| 消息类型 | 上限 | 说明 |
|----------|------|------|
| `TextMessage` | 2048 字节（UTF-8） | 内容为空或超长时构造失败，属于调用方参数错误 |
| `MarkdownMessage` | 4096 字节（UTF-8） | 同上 |

空的 `mentioned_list` / `mentioned_mobile_list` 不会出现在 JSON 中，避免无意义的空数组。

### access_token 管理

1. 首次发送前调用 `/cgi-bin/gettoken` 获取 token
2. 缓存在 `AtomicReference` 中，提前 **5 分钟**视为过期
3. 刷新采用**双重检查 + 同步锁**，避免并发请求重复获取 token
4. 发送返回 `40014 / 42001 / 41001` 时，通过 CAS 仅清除**本次使用的** token，然后自动重试一次
5. 重试仍失败则抛出 `WeChatException`，由调用方决定是否降级

### HTTP 超时与异常

| 实现 | 连接超时 | 读取/请求超时 |
|------|---------|--------------|
| JDK17 `HttpClient` | 5s | 10s |
| Spring `RestClient` | 5s | 10s |

- HTTP 非 2xx：抛出 `WeChatException`，消息中包含状态码与响应片段
- 网络异常 / 中断：转换为 `WeChatException`；中断时恢复线程中断标记
- 日志中的 Webhook、access_token 等查询参数会打码为 `?***`

## 错误码与排查

| errcode | 含义 | 排查方向 |
|---------|------|---------|
| 40001 | 不合法的 secret | 检查应用 Secret 是否复制完整、是否属于该应用 |
| 40013 | 不合法的 CorpID | 检查企业 ID |
| 40014 | 不合法的 access_token | 删除缓存后重试；检查 token 是否被其他实例覆盖 |
| 41001 | 缺少 access_token | 检查请求 URL 拼接 |
| 42001 | access_token 已过期 | 代码已自动刷新重试，持续出现需检查系统时间 |
| 45009 | 接口调用超过频率限制 | 降低发送频率，群机器人上限 20 条/分钟 |
| 60020 | 不在可信 IP 列表 | 管理后台为自建应用配置可信 IP |
| 81013 | user/party/tag 全部无效 | 核对 userid、部门 ID；成员是否已离职 |
| 93000 | 群机器人 Webhook 地址无效 | 检查 key 是否正确、机器人是否被移除 |

> 错误码含义以企业微信官方文档为准；本表仅列出常见场景。

## 构建与测试

```bash
# 全量编译 + 测试（44 个测试）
mvn clean test

# 打包（含 Spring Boot 可执行 jar）
mvn clean package

# 运行 Spring Boot 可执行 jar（需先配置环境变量）
java -jar wechat-alert-springboot3/target/wechat-alert-springboot3-1.0.0.jar
```

测试覆盖：

- `wechat-alert-core`：消息序列化、空 @ 列表省略、长度校验、token 响应空值安全、错误码判断
- `wechat-alert-jdk17`：本地 HTTP 服务模拟企业微信，验证请求体、token 缓存、过期重试、HTTP/业务错误
- `wechat-alert-springboot3`：`MockRestServiceServer` 验证 token 缓存与重试、服务层部分失败语义、REST 接口状态码与错误结构、默认配置启动

## 安全建议

1. **不要把 webhook key / secret 提交到仓库**：使用环境变量或配置中心；本项目的 `application.yml` 默认关闭通道
2. **Webhook 即凭证**：拿到 URL 的任何人都能往群里发消息，泄露后立即在群设置中移除并重建机器人
3. **REST API 默认无鉴权**：本项目仅为 Demo，生产环境请放在内网/网关后，并增加认证、限流、审计
4. **配置可信 IP**：自建应用建议在管理后台配置可信 IP，避免 secret 泄露后被外部调用
5. **日志脱敏**：已对 URL 查询串打码，但仍应避免在业务日志中打印 secret / token

## 常见问题

**Q：Demo 运行后只打印“已跳过”？**
未设置对应环境变量。按「快速开始」配置 `WECHAT_GROUP_WEBHOOK` 或自建应用的四项参数。

**Q：Spring Boot 启动报 `agentId 必须是数字`？**
`wechat.alert.app.agent-id` 仍是占位符或不是数字。改为真实 AgentId（数字字符串），或先关闭 `wechat.alert.app.enabled`。

**Q：群消息发送成功但没有 @ 到人？**
群机器人的 `mentioned_list` 使用 **userid**（通讯录账号），`mentioned_mobile_list` 使用手机号；`@all` 通过 `mentioned_list: ["@all"]`。

**Q：自建应用能发给全员，群机器人不能？**
群机器人只能发到它所在的群；需要按人推送请使用自建应用。

**Q：Markdown 样式不生效？**
群机器人仅支持有限 Markdown 语法（标题、加粗、链接、引用、`<font color="info|comment|warning">` 等），不支持完整 HTML。

**Q：多实例部署会不会重复刷新 token？**
每个实例独立缓存并加锁；实例之间不共享缓存。通常可接受；如需严格单点刷新，可将 token 放入 Redis 等共享缓存。

## 变更记录（本次改进）

**修复**

- 修复默认 `application.yml` 使用占位符导致 Spring Boot 启动时 `NumberFormatException` 的问题；通道默认关闭，启用后缺少配置会**快速失败并给出明确提示**
- 修复 `gettoken` 响应缺少 `errcode` / `expires_in` 时的 `NullPointerException` / `ClassCastException`，改用空值安全的 `TokenResponse`
- 修复并发刷新 token 的竞争问题：双重检查 + 同步锁 + CAS 清除，避免误清其他线程刚获取的 token
- 修复全局异常处理中 `Map.of` 在异常 message 为 null 时抛 `NullPointerException` 的问题
- 修复参数缺失/JSON 格式错误被统一当成 500 的问题，现分别返回 400 / 404
- 修复 `RestClient` 未设置超时、HTTP 错误未转换为 `WeChatException` 的问题

**改进**

- 消息发送前校验非空与字节长度；空 @ 列表不再序列化
- `/api/wechat/alert` 返回各通道结果，部分失败可感知；全部失败返回 502
- JDK17 Demo 改为读取环境变量/系统属性，未配置时跳过而不是报错
- 调试日志对 webhook / token 查询串打码
- 新增 44 个单元/集成测试与本文档

## 已知限制

- 仅实现 `text` / `markdown` 两种消息类型；图片、图文、文件、模板卡片等需按官方文档扩展 `MsgType` 与消息模型
- 未内置重试退避与限流；生产环境建议由上层告警系统负责重试、去重与降级
- `access_token` 缓存在进程内，多实例不共享
- REST API 无认证，仅适合内网或 Demo 使用
- 未提供异步批量发送；高并发场景建议配合线程池/消息队列

## 技术栈

- JDK 17（record、text block、`java.net.http.HttpClient`）
- Jackson 2.17
- Spring Boot 3.5 / Spring 6.2（`RestClient`、`@ConfigurationProperties`、自动配置）
- JUnit 5、MockRestServiceServer、JDK `HttpServer`（本地集成测试）
