# Fast AI Boot 3 Starter

基于 Spring Boot 3 和 Spring AI `1.1.8` 的通用企业 AI 能力 starter，提供统一对话、流式输出、LLM Tool 语义路由、用户级持久化长短期记忆和 APT Tool Calling。

完整的总体架构、模块划分、核心流程、数据模型、安全机制与演进规划见：[设计说明](doc/设计说明.md)。

可运行的 DeepSeek 业务接入示例位于 [`examples/fast-ai-business-demo`](examples/fast-ai-business-demo)，包含 Spring Boot 3.4.3 后端和 Vite + Vue 3 前端。示例使用独立 Gradle 构建，不会进入 starter 的正式制品。

## Provider 依赖规则

入口 starter 不会同时携带多个模型实现。业务系统按照实际 Provider 选择一个入口即可。

### OpenAI

```groovy
dependencies {
    implementation 'io.github.lyx9527:fast-ai-boot3-starter:1.0.0-SNAPSHOT'

    annotationProcessor 'io.github.lyx9527:fast-ai-boot3-processor:1.0.0-SNAPSHOT'
}

dependencyManagement {
    imports {
        mavenBom 'org.springframework.ai:spring-ai-bom:1.1.8'
    }
}
```

### DeepSeek

DeepSeek 必须使用独立的 Spring AI DeepSeek starter：

```groovy
dependencies {
    implementation 'io.github.lyx9527:fast-ai-boot3-starter-deepseek:1.0.0-SNAPSHOT'

    annotationProcessor 'io.github.lyx9527:fast-ai-boot3-processor:1.0.0-SNAPSHOT'
}
```

### 其他 OpenAI 协议兼容厂商

其他兼容 OpenAI Chat Completions 协议的厂商仍使用默认入口：

```groovy
implementation 'io.github.lyx9527:fast-ai-boot3-starter:1.0.0-SNAPSHOT'
```

然后配置厂商的 `base-url` 和模型名称。

> 不要在同一个业务应用中同时添加 OpenAI 和 DeepSeek starter，除非业务系统自行管理多个 `ChatModel`。

## 最小配置

OpenAI：

```yaml
fast:
  ai:
    provider: openai
    api-key: ${OPENAI_API_KEY}
```

DeepSeek：

```yaml
fast:
  ai:
    provider: deepseek
    api-key: ${DEEPSEEK_API_KEY}
```

其他 OpenAI 协议兼容厂商：

```yaml
fast:
  ai:
    provider: openai-compatible
    api-key: ${PROVIDER_API_KEY}
    base-url: https://api.example.com
    model: example-chat-model
```

starter 会将统一配置映射为 Spring AI `1.1.8` 的原生配置：

| `fast.ai.provider` | Spring AI 模型 | 所需依赖 |
|---|---|---|
| `openai` | `openai` | `fast-ai-boot3-starter`，内部使用 `spring-ai-starter-model-openai` |
| `deepseek` | `deepseek` | `fast-ai-boot3-starter-deepseek`，内部使用 `spring-ai-starter-model-deepseek` |
| `openai-compatible` | `openai` | `fast-ai-boot3-starter`，内部使用 `spring-ai-starter-model-openai` |

业务显式配置的 `spring.ai.*` 属性优先级高于 starter 的映射值。

## 对话服务

```java
import io.github.lyx9527.fastai.chat.AiChatRequest;
import io.github.lyx9527.fastai.chat.AiChatResponse;
import io.github.lyx9527.fastai.chat.AiChatService;
import org.springframework.stereotype.Service;

@Service
public class CustomerAssistant {

    private final AiChatService aiChatService;

    public CustomerAssistant(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    public AiChatResponse chat(String userId, String conversationId, String message) {
        return this.aiChatService.chat(AiChatRequest.builder()
                .tenantId("company-a")
                .userId(userId)
                .conversationId(conversationId)
                .message(message)
                .build());
    }
}
```

流式输出：

```java
Flux<AiChatChunk> chunks = aiChatService.stream(request);
```

`AiChatChunk` 使用三类流式事件：

- `CONTEXT`：正式生成前返回预估上下文占用和本轮压缩结果。
- `DELTA`：模型生成的文本增量。
- `COMPLETE`：流结束，携带 Provider 返回的实际 Prompt Token；Provider 未返回 Usage 时保留估算值。

流式调用会将同步 Tool 语义路由、JDBC 上下文准备和完成后的历史持久化调度到 `boundedElastic`，不会在 WebFlux 的 `reactor-http-nio` 事件线程执行阻塞操作；模型文本流仍通过 Reactor 持续输出。

`occupancyRate` 表示本轮发送给模型的 Prompt 占用率，不包含模型正在生成的 Completion。当前回答会在下一轮作为历史消息进入 Prompt，因此“回答很长但本轮水位较低”属于正常现象。页面如需展示下一轮预估水位，可以使用“本轮实际 Prompt + 本轮 Completion”进行近似计算。

Spring MVC SSE 示例：

```java
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<AiChatChunk>> stream(ChatRequest request) {
    return aiChatService.stream(toAiRequest(request))
            .map(chunk -> ServerSentEvent.builder(chunk)
                    .event(chunk.eventType().name().toLowerCase())
                    .build());
}
```

上下文事件示例：

```json
{
  "content": "",
  "finalChunk": false,
  "eventType": "CONTEXT",
  "contextUsage": {
    "estimatedPromptTokens": 28640,
    "actualPromptTokens": null,
    "maxContextTokens": 64000,
    "occupancyRate": 0.4475,
    "compressed": true,
    "tokensBeforeCompression": 53120,
    "messagesBeforeCompression": 86,
    "messagesAfterCompression": 9,
    "summarizedMessages": 78,
    "cumulativePromptTokens": 128540,
    "cumulativeCompletionTokens": 8640,
    "cumulativeTotalTokens": 137180,
    "conversationRequestCount": 23
  }
}
```

## 长上下文压缩

压缩发生在正式对话请求前：

1. 估算系统提示词、长期记忆、短期消息、当前问题和 Tool Schema 的 Token。
2. 达到压缩阈值后，保留最近消息。
3. 更早的历史按照批次调用模型摘要，避免摘要请求本身超过上下文窗口。
4. 多个分批摘要再次合并为滚动摘要。
5. 将摘要和最近消息回写 `ChatMemory`，再执行正式对话。

```yaml
fast:
  ai:
    context:
      compression-enabled: true
      max-context-tokens: 64000
      reserved-output-tokens: 4096
      compression-threshold: 0.8
      target-occupancy: 0.55
      preserve-recent-messages: 8
      compression-batch-tokens: 12000
      summary-max-tokens: 2000
```

其中 `reserved-output-tokens` 为模型输出预留空间，压缩阈值基于扣除预留空间后的 Prompt 预算计算。

## 用户级记忆

短期记忆按以下作用域隔离：

```text
tenantId + userId + conversationId
```

实际存储 Key 使用 SHA-256，避免直接暴露租户和用户标识。

默认能力：

- 短期记忆：Spring AI `MessageWindowChatMemory` + JDBC Repository，只保存模型下一轮需要使用的上下文窗口，默认最多保留 200 条消息。
- 完整历史：`AiConversationHistoryStore` 追加保存每轮用户和助手消息，不受短期窗口裁剪或长上下文压缩影响。
- 长期记忆：JDBC 持久化，按 `tenantId + userId` 隔离。
- 自动提取：对话结束后异步提取稳定用户事实和偏好。
- 业务接口：可注入 `AiMemoryService` 主动保存、查询和删除记忆。
- 长上下文压缩：摘要和保留的最近消息会重新写回数据库，不会只保存在当前 JVM。
- 扩展：声明自己的 `AiLongTermMemoryStore` Bean 可替换为 VectorStore 等实现。

### 默认 SQLite

业务系统不配置存储类型时，starter 自动使用文件型 SQLite，同时创建短期上下文、完整历史、长期记忆和累计用量表：

```yaml
fast:
  ai:
    persistence:
      type: sqlite
      sqlite:
        file: data/fast-ai.db
        maximum-pool-size: 4
```

默认数据库文件为当前应用工作目录下的 `data/fast-ai.db`。不支持 `:memory:` 或 `mode=memory` SQLite 配置。

SQLite 连接默认启用 WAL 和写锁等待，适用于单实例或轻量部署。多实例企业部署建议使用 MySQL。

### 使用 MySQL

业务应用增加 MySQL 驱动：

```groovy
dependencies {
    runtimeOnly 'com.mysql:mysql-connector-j'
}
```

然后切换持久化类型：

```yaml
fast:
  ai:
    persistence:
      type: mysql
      mysql:
        url: jdbc:mysql://127.0.0.1:3306/fast_ai?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
        username: fast_ai
        password: ${FAST_AI_DB_PASSWORD}
        maximum-pool-size: 10
```

如需复用业务系统的数据源，可以声明名称固定为 `fastAiPersistenceDataSource` 的 `DataSource` Bean；starter 将不再创建自己的连接池。

首次启动会自动创建：

- `fast_ai_chat_memory`：持久化模型短期上下文窗口，允许被窗口策略裁剪或被长上下文压缩结果覆盖。
- `fast_ai_conversation_history`：追加持久化完整用户/助手对话，不受短期窗口裁剪影响。
- `fast_ai_long_term_memory`：持久化租户与用户级长期事实、偏好、TTL 和扩展元数据。
- `fast_ai_conversation_usage`：按脱敏会话 Key 持久化累计输入、输出、总 Token 和成功请求次数。

业务系统可以注入 `AiConversationHistoryStore` 查询完整历史；`AiChatService.clearConversation(...)` 会同时清理该会话的短期上下文、完整历史和累计 Token 数据。

配置示例：

```yaml
fast:
  ai:
    memory:
      short-term:
        enabled: true
        max-messages: 30
      long-term:
        enabled: true
        auto-extract: true
        top-k: 5
        max-extracted-per-turn: 3
        ttl: 180d
```

## LLM Tool 语义路由

业务系统不再维护意图编码、关键词、示例或意图到 Tool 的映射。starter 自动读取 APT Tool 元数据：

- `@LLMFunctionCalling` 提供 Tool 名称和业务描述。
- `@LLMToolGroup` 提供能力分组。
- `@LLMToolSet` 提供工具集名称和业务说明。

请求未显式指定 Tool 时，独立路由模型只接收上述精简目录，不接收全部 Tool 参数 Schema。路由模型根据用户语义返回需要的 Tool 名称，正式对话再只注入被选中的 Tool Schema。

默认路由顺序如下：

1. 请求通过 `toolNames`、`toolGroups` 或 `toolSets` 显式选择时直接加载，跳过 LLM 语义路由。
2. 未显式选择且 `semantic-routing.enabled=true` 时，使用 LLM 根据当前消息、最近对话和 Tool 业务描述进行语义选择。
3. 只接受注册表中真实存在、达到置信度阈值且不超过数量上限的 Tool 名称。
4. 普通闲聊、低置信度、模型异常或返回未知 Tool 时，本轮注入 0 个 Tool，不回退为全量注入。
5. 只有关闭语义路由后，`include-all-when-unspecified` 才作为兼容模式生效。

配置仅用于控制路由策略，不包含任何业务意图词库：

```yaml
fast:
  ai:
    tools:
      enabled: true
      include-all-when-unspecified: false
      semantic-routing:
        enabled: true
        confidence-threshold: 0.7
        max-selected-tools: 5
        catalog-batch-size: 24
        history-messages: 6
```

当注册 Tool 超过 `catalog-batch-size` 时，starter 会分批调用路由模型召回候选，并逐轮收敛后再做最终选择。单次路由请求看到的 Tool 数量保持有界，不会随着全部 Tool 数量线性扩大。

请求可以用 `.toolsEnabled(false)` 禁止本轮通过显式选择或 LLM 语义路由注入任何 Tool。

## APT Tool Calling

业务 Service：

```java
import io.github.lyx9527.fastai.annotation.InjectCtx;
import io.github.lyx9527.fastai.annotation.LLMFunctionCalling;
import io.github.lyx9527.fastai.annotation.LLMParameter;
import io.github.lyx9527.fastai.annotation.LLMToolGroup;
import io.github.lyx9527.fastai.annotation.LLMToolRiskLevel;
import io.github.lyx9527.fastai.annotation.LLMToolSecurity;
import io.github.lyx9527.fastai.annotation.LLMToolSet;
import org.springframework.stereotype.Service;

@Service
@LLMToolSet(name = "order-tools", description = "订单查询与操作工具集")
@LLMToolGroup("order")
@LLMToolSecurity(
        risk = LLMToolRiskLevel.READ_ONLY,
        permissions = "order:read")
public class OrderService {

    @LLMFunctionCalling(
            name = "order-query",
            description = "查询当前用户的订单",
            groups = {"query"})
    public OrderDTO query(
            @LLMParameter(description = "订单号") String orderNo,
            @InjectCtx("userId") String userId,
            @InjectCtx("tenantId") String tenantId) {
        return queryFromDatabase(tenantId, userId, orderNo);
    }

    @LLMFunctionCalling(
            name = "order-cancel",
            description = "取消当前用户的订单")
    @LLMToolGroup("write")
    @LLMToolSecurity(
            risk = LLMToolRiskLevel.DANGEROUS,
            permissions = "order:cancel",
            requireConfirmation = true)
    public boolean cancel(
            @LLMParameter(description = "订单号") String orderNo,
            @InjectCtx("userId") String userId,
            @InjectCtx("tenantId") String tenantId) {
        return cancelOrder(tenantId, userId, orderNo);
    }
}
```

编译期将生成 Spring Bean Tool Adapter：

- LLM 只能看到 `orderNo`。
- `userId`、`tenantId` 从可信 ToolContext 注入。
- 生成 Spring AI `ToolCallback` 和 JSON Schema。
- `@LLMToolSet` 将一个业务 Service 声明为可按需选择的工具集。
- 类级 `@LLMToolGroup`、方法级 `@LLMToolGroup` 与 `@LLMFunctionCalling.groups` 自动合并去重。
- 方法级 `@LLMToolSecurity` 整体覆盖类级安全配置；未声明时继承类级配置。
- DTO、集合、枚举和嵌套对象由 Spring AI/Jackson 完成转换。
- 重复工具名称、非法工具名称、非 public 方法在编译期报错。

按名称注入工具：

```java
AiChatRequest request = AiChatRequest.builder()
        .message("帮我查询订单 20260818001")
        .userId("u-1001")
        .conversationId("c-1")
        .addTool("order-query")
        .build();
```

按分组注入：

```java
.addToolGroup("order")
```

按工具集注入：

```java
.addToolSet("order-tools")
```

工具执行安全上下文由业务系统在认证、鉴权后写入请求：

```java
AiChatRequest request = AiChatRequest.builder()
        .message("取消订单 20260818001")
        .tenantId(authenticatedTenantId)
        .userId(authenticatedUserId)
        .conversationId(conversationId)
        .addToolSet("order-tools")
        .addPermission("order:cancel")
        .confirmTool("order-cancel")
        .build();
```

安全规则：

- 缺少 `permissions` 中任一权限时，Tool 在执行前被拒绝。
- `requireConfirmation = true` 或风险级别为 `DANGEROUS` 时，必须通过 `confirmTool` 显式确认具体工具名。
- 默认记录工具名、风险级别、租户、用户及执行结果，不记录 Tool 入参。
- `permissions` 和 `confirmedToolNames` 必须由可信服务端逻辑生成，不能直接透传客户端字段。
- 请求 `metadata` 即使包含同名字段，也无法覆盖可信的租户、用户、权限和确认信息。

默认不会把所有工具暴露给模型。如需兼容旧系统，在明确关闭 LLM 语义路由后才可启用全量注入：

```yaml
fast:
  ai:
    tools:
      include-all-when-unspecified: true
      semantic-routing:
        enabled: false
```

## 自定义扩展点

业务系统可以提供同类型 Bean 覆盖默认实现：

- `ChatModel`
- 名为 `fastAiChatMemoryRepository` 的 `ChatMemoryRepository`
- 名为 `fastAiPersistenceDataSource` 的 `DataSource`
- 名为 `fastAiChatMemory` 的 `ChatMemory`
- 名为 `fastAiConversationUsageStore` 的 `AiConversationUsageStore`
- 名为 `fastAiConversationHistoryStore` 的 `AiConversationHistoryStore`
- `AiLongTermMemoryStore`
- `AiMemoryExtractor`
- `AiConversationKeyFactory`
- `AiToolSecurityEvaluator`
- `AiToolRegistry`
- `AiToolSelectionService`
- `AiChatService`

## 构建与测试

```bash
./gradlew clean test
```

发布到本机 Maven 仓库进行业务项目联调：

```bash
./gradlew publishToMavenLocal
```

当前项目使用：

- Java 17
- Spring Boot `3.5.15`
- Spring AI BOM `1.1.8`
- Gradle `9.3.0`
