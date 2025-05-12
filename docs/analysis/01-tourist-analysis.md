# jingbanyou-tourist 模块代码审查与优化分析

**模块路径**: `jingbanyou-tourist/`
**审查日期**: 2026-05-12
**最后更新**: 2026-05-13
**审查范围**: TouristController、StreamingAnswerService、ChatMemoryService、StreamGraphConfiguration、TtsService、TranscribeService、EmotionDetectServiceImpl、ProfileVectorStoreService、HybridRetrievalNode、MapRouteApiInvokerNode

---

## 1. 概述

jingbanyou-tourist 是 AI 导游系统的游客端核心模块，基于 Spring Boot 3.5.0 + Spring AI Alibaba Graph 构建，通过 Graph 架构实现意图识别、景点问答、路线规划等功能。模块包含 8 个 Graph 节点、10 个 Service、8 个 ChatClient 配置，整体功能完整。

> **2026-05-13 更新**：已引入 RabbitMQ 异步架构，通过消息队列解耦请求接收与业务处理，新增 `POST /tourist/chat` + `GET /tourist/stream/{conversationId}` 两个端点。同步进行了 Controller 拆分（按领域拆分为 BootstrapController / ChatController / VoiceController / ConversationController）、SSE 事件工厂化（SseEventFactory）、Redis 流桥接（SseResultPublisher + SseStreamBridge）、游客会话管理（TouristSessionFilter + TouristSessionService）等多项架构重构。详见第 3.5~3.7 节及第 7 节。

本次审查覆盖了模块的核心代码，从**代码质量、架构设计、性能、安全、可维护性**五个维度进行深入分析，共发现高优先级问题 6 个、中优先级 9 个、低优先级 5 个。

---

## 2. 代码质量分析

### 高优先级

#### 2.1 TouristController 依赖注入过多（上帝 Controller）✅ 已修复 (2026-05-13)

**原问题**: TouristController 注入了 11 个 Service/配置对象，违背单一职责原则。单个文件超过 520 行，Controller 同时承担了 Graph 执行、SSE 事件构建、业务调度等多种职责。

**修复方案**: 按领域拆分为 4 个聚焦的 Controller，删除 TouristController：

| 新 Controller | 端点 | 依赖数 |
|---|---|---|
| `BootstrapController` | `GET /tourist/bootstrap` | 5 |
| `ChatController` | `POST /tourist/stream`, `/chat`, `/chat/end`, `GET /tourist/stream/{id}` | 11 |
| `VoiceController` | `POST /tourist/voice/transcribe`, `GET /tourist/tts`, `/tts/{filename}` | 3 |
| `ConversationController` | `GET /tourist/conversation/list`, `/conversation/{sessionId}` | 1 |

同时删除了 `ITouristFacadeService` / `TouristFacadeServiceImpl`，业务编排逻辑作为 private 方法内联到各 Controller 中。ChatRequestConsumer 同步重构，直接注入 5 个聚焦 Service 替代 facadeService。

---

#### 2.2 StreamingAnswerService.streamAnswer 参数过多

**文件**: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/service/impl/StreamingAnswerService.java`

**问题描述**: `streamAnswer` 方法有 12 个参数（第 54-66 行），违反了方法参数数量不超过 5 个的最佳实践。调用处（TouristController 第 239-242 行）需要传递大量状态。

```java
public Flux<ServerSentEvent<String>> streamAnswer(
    String sessionId,
    Long scenicId,
    Long humanId,
    String visitorId,
    String intent,
    String userMessage,
    String retrievedDocs,
    DigitalHumanConfig digitalHuman,
    List<?> rawRoutes,
    String intentType,
    int graphCostMs,
    long startTimestamp)
```

**影响范围**: TouristController 调用处，任何参数变更都需要同时修改两处。

**建议方案**: 创建 `StreamAnswerContext` DTO，将相关参数封装为上下文对象，减少调用方的传参复杂度，也便于后续扩展（如增加参数时无需改签名）。

---

#### 2.3 手动 JSON 字符串拼接构建 SSE ✅ 已修复 (2026-05-12)

**原文件**:
- `TouristController.java`（已删除，SSE 构建已迁移至 SseEventFactory）
- `StreamingAnswerService.java`

**问题描述**: SSE 事件数据通过字符串拼接构建，没有使用 JSON 库（如 Jackson/ObjectMapper）。`escapeJson` 方法只处理了 5 种字符（`\`, `"`, `\n`, `\r`, `\t`），遗漏了其他需要转义的字符如 `/`、`\b`、`\f`。手工拼接容易产生格式错误和安全漏洞。

```java
private String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
}
```

`audioSse` 方法同样手工拼接（第 287-296 行），存在格式化错误风险。

**影响范围**: 所有 SSE 响应，任何 JSON 格式数据的内容均可能出错。

**建议方案**:
1. 使用 `ObjectMapper` 的 `writeValueAsString` 统一序列化
2. 创建 `SseEventFactory` 类封装所有 SSE 事件构建逻辑
3. 考虑引入 Jackson 的序列化方式替代手写 JSON

---

#### 2.4 代码重复：escapeJson 在两个类中各有一份

**文件**:
- `TouristController.java`（已删除，escapeJson 已统一由 SseEventFactory 替代）
- `StreamingAnswerService.java`

**问题描述**: `escapeJson` 方法在 TouristController 和 StreamingAnswerService 中完全重复（第 327-334 行 vs 第 308-315 行），两处逻辑一模一样。

**影响范围**: 两处均使用，如需修改转义逻辑需要同时改两处。

**建议方案**: 提取到 `SseEventFactory` 或通用工具类 `JsonEscapeUtil`。

---

### 中优先级

#### 2.5 ChatMemoryService.syncToMySQL 逻辑过长

**文件**: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/service/impl/ChatMemoryService.java`

**问题描述**: `syncToMySQL` 方法（第 62-168 行）约 106 行，承担了过多职责：
1. 从 Redis 读取对话消息
2. 遍历构建 VisitorInteraction 记录
3. 读取情感数据（Redis）
4. 计算会话时长（雪花 ID 反推时间戳）
5. 写入 VisitorConversation
6. 清除 Redis 数据

每一步都有独立的 try-catch 块，逻辑嵌套深。

**影响范围**: 会话持久化流程，任何变更都可能导致数据不一致。

**建议方案**: 拆分为多个私有方法：`loadMessages`、`loadEmotionData`、`calculateDuration`、`persistConversation`。雪花 ID 反推时间戳的逻辑应提取为工具方法并添加单元测试。

---

#### 2.6 代码重复：buildRouteSummary / buildAttachmentsJson 手工拼接

**原文件**:
- `TouristController.java`（已删除，路线附件构建已迁移至 RouteAttachmentBuilder）
- `StreamingAnswerService.java`

**问题描述**: 路线摘要和附件 JSON 的构建在 TouristController 和 StreamingAnswerService 中分别手工拼接。`buildAttachmentsJson`（第 279-293 行）手动拼接 JSON 数组，`buildRouteSummary`（第 296-306 行）处理路线摘要格式。

**影响范围**: 路线规划功能，格式一致性难以保证。

**建议方案**: 创建 `RouteAttachmentBuilder` 类统一处理，或使用 Jackson 的 `ObjectMapper` 序列化。

---

#### 2.7 无统一错误码体系

**原文件**: `TouristController.java`（已删除）

**原始问题**: 所有错误通过 `error("消息文本")` 返回，错误信息硬编码在 Controller 中。没有错误码枚举，客户端无法程序化地处理不同类型的错误。

```java
return error("景区ID不能为空");
return error("景区不存在");
return error("会话不存在");
```

**影响范围**: 游客端所有 API 调用方。

**建议方案**: 定义 `TouristErrorCode` 枚举，统一错误码（如 `SCENIC_NOT_FOUND = "T001"`），修改 `BaseController.error()` 支持错误码参数。

---

#### 2.8 MultipartFile 无大小限制 ✅ 已修复 (2026-05-12)

**原文件**: `TouristController.java`（已删除，该接口已迁移至 VoiceController）

**问题描述**: `/voice/transcribe` 接口的 `MultipartFile` 参数没有配置文件大小限制，可能导致大文件上传攻击或内存溢出。

```java
public AjaxResult transcribe(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "language", required = false, defaultValue = "zh") String language)
```

**影响范围**: 语音转文字接口，可能被恶意大文件请求攻击。

**建议方案**: 在 `application.yml` 中配置 `spring.servlet.multipart.max-file-size=10MB`，并添加文件类型白名单校验。

---

### 低优先级

#### 2.9 recordSingleTurn 空实现

**文件**: `ChatMemoryService.java` 第 196-203 行

**问题描述**: `recordSingleTurn` 方法仅打印日志，没有实际功能，但仍然是公开接口，会造成调用方困惑。

**建议方案**: 删除该方法或添加 `@Deprecated` 注解，并清理调用方。

---

#### 2.10 EmotionDetectServiceImpl 的 Prompt 硬编码在代码中

**文件**: `EmotionDetectServiceImpl.java` 第 109-130 行

**问题描述**: AI 情感分析的 Prompt 以多行字符串硬编码在代码中，不利于调优和维护。情感分析规则词表（POSITIVE_WORDS / NEGATIVE_WORDS）也硬编码在类中。

**建议方案**: 将 Prompt 和规则词表迁移到配置文件。

---

#### 2.11 HybridRetrievalNode 和 MapRouteApiInvokerNode 的 try-catch 过于宽泛

**文件**:
- `HybridRetrievalNode.java` 全文
- `MapRouteApiInvokerNode.java` 全文

**问题描述**: 两节点的 `apply` 方法都直接 `throws Exception`，上游 Graph 框架捕获后可能丢失具体错误类型，不利于问题定位。

**建议方案**: 捕获具体异常类型（`VectorStoreException`、`JsonProcessingException` 等），分类处理。

---

## 3. 架构分析

### 中优先级

#### 3.1 Controller 混合 SSE 事件构建和业务逻辑 ✅ 已修复 (2026-05-12)

**原问题**: Controller 既执行 Graph、编排服务，又手工构建 SSE 事件。SSE 事件构建与业务逻辑耦合。

**修复**: 抽取 `SseEventFactory`，将所有 SSE 事件构建逻辑独立。Controller 已按领域拆分为 4 个（BootstrapController / ChatController / VoiceController / ConversationController），详见 2.1 节。

---

#### 3.2 ChatMemoryService 混合 Redis 元数据管理和 MySQL 持久化

**文件**: `ChatMemoryService.java`

**问题描述**: `ChatMemoryService` 同时负责：
1. Redis 元数据操作（`saveChatMetadata`、`getChatMetadata`、`deleteChatMetadata`）
2. MySQL 持久化（`syncToMySQL`）
3. 情感数据更新（`updateLastInteractionEmotion`）

违反单一职责原则，两类数据存储的操作混在一起。

**建议方案**: 拆分为 `ChatMetadataService`（Redis）和 `ConversationPersistenceService`（MySQL）。

---

#### 3.3 Graph 节点通过 shared state 紧耦合

**文件**: `StreamGraphConfiguration.java` + 各 Node

**问题描述**: Graph 各节点之间通过 `OverAllState` 传递数据，节点间通过共享状态耦合。以 `HybridRetrievalNode` 为例，它写入 `RETRIEVED_DOCS`，下游 `StreamingAnswerService` 读取该值。当状态 key 变更时，多个组件需要同步修改。

**影响范围**: Graph 架构整体，状态 key 变更成本高。

**建议方案**: 为 Graph State Key 定义常量接口并集中管理；引入 Proto 或 JSON Schema 定义状态结构。

---

#### 3.4 TranscribeService 和 TtsService 未抽取为接口实现

**文件**:
- `TranscribeService.java`
- `TtsService.java`

**问题描述**: 两个 Service 直接实现接口，但接口定义与实现在同一模块，无法跨模块替换实现（如从 DashScope 切换到其他供应商）。

**建议方案**: 接口放入 `jingbanyou-common`，实现在 `jingbanyou-tourist` 中。

---

#### 3.5 多控制器拆分——Controller 重构 ✅ 已完成 (2026-05-13)

**改造内容**: 将原 `TouristController`（10 个端点）按领域拆分为 4 个聚焦的 Controller，删除 `ITouristFacadeService` 门面接口及其实现。业务编排逻辑作为 private 方法内联到各 Controller 中。

**新 Controller 清单**:

| Controller | 端点 | 依赖 |
|---|---|---|
| `BootstrapController` | `GET /tourist/bootstrap` | IBootstrapCacheService, IScenicAreaService, IDigitalHumanConfigService, IFaqCacheService, TouristSessionService |
| `ChatController` | `POST /tourist/stream`, `/chat`, `/chat/end`, `GET /tourist/stream/{id}` | StreamGraphConfiguration, IStreamingAnswerService, SseEventFactory, IChatMetadataService, IConversationPersistenceService, IEmotionDetectService, IDigitalHumanConfigService, TouristSessionService, RabbitTemplate, SseStreamBridge, Tracer |
| `VoiceController` | `POST /tourist/voice/transcribe`, `GET /tourist/tts`, `/tts/{filename}` | ITranscribeService, ITtsService, IDigitalHumanConfigService |
| `ConversationController` | `GET /tourist/conversation/list`, `/conversation/{sessionId}` | IVisitorConversationService |

**删改文件**:
- 删除 `TouristController.java`
- 删除 `ITouristFacadeService.java`
- 删除 `TouristFacadeServiceImpl.java`
- `ChatRequestConsumer.java` 同步重构，移除 facadeService 依赖，改注 5 个聚焦 Service

**效果**: 每个 Controller 职责单一，依赖聚焦。ChatController 仍然是 11 个依赖（对话编排本身复杂），但不再混入首屏/语音/会话历史逻辑。

---

#### 3.6 RabbitMQ 异步对话体系 ✅ 已完成 (2026-05-13)

**组件清单**:

| 组件 | 位置 | 作用 |
|------|------|------|
| `RabbitMQConfig.java` | `jingbanyou-tourist/.../config/` | 声明 TopicExchange、主队列、死信交换机（DLX）、死信队列（DLQ）及绑定关系，配置 Jackson2JsonMessageConverter |
| `ChatRequestMessage.java` | `jingbanyou-tourist/.../dto/` | RabbitMQ 消息 DTO（Java record），包含 conversationId、message、sessionId、scenicId、visitorId |
| `ChatRequestConsumer.java` | `jingbanyou-tourist/.../consumer/` | `@RabbitListener` 消费者，接收消息后执行 Graph，通过 SseResultPublisher 推送结果到 Redis |

**请求链路**:

```
POST /tourist/chat -> Controller(立即返回 conversationId) -> RabbitMQ(TopicExchange)
  -> ChatRequestConsumer(线程池) -> executeGraph -> SseResultPublisher(Redis)
     -> GET /tourist/stream/{cid}(SSE) -> SseStreamBridge(Redis Pub/Sub -> Flux)
```

**关键配置**:
```yaml
spring.rabbitmq.listener.simple.concurrency=5       # 并发消费者数
spring.rabbitmq.listener.simple.max-concurrency=20  # 最大消费者数
spring.rabbitmq.listener.simple.prefetch=1          # 公平分发
```

**优点**: 解耦 Tomcat 线程与 Graph AI 调用，避免 AI 长耗时阻塞请求线程；死信队列兜底失败消息。

---

#### 3.7 SSE 流桥接体系 ✅ 已完成 (2026-05-13)

**组件清单**:

| 组件 | 位置 | 作用 |
|------|------|------|
| `SseEventFactory.java` | `jingbanyou-tourist/.../service/sse/` | SSE 事件工厂，统一构建 answer_fragment、audio、answer、metadata、done、error 六种事件 |
| `SseMessage.java` | `jingbanyou-tourist/.../dto/` | SSE 事件传输 DTO（Java record），包含 event、data、id、timestamp 字段 |
| `SseResultPublisher.java` | `jingbanyou-tourist/.../service/sse/` | 将 SSE 事件写入 Redis List（持久化，支持 late-joiner）+ Redis Pub/Sub（实时推送），TTL=300s |
| `SseStreamBridge.java` | `jingbanyou-tourist/.../service/sse/` | Redis Pub/Sub -> Flux\<ServerSentEvent\> 桥接：先回放 Redis List 历史事件，再订阅 Pub/Sub 实时事件，完成后自动取消订阅 |

**设计要点**:
- Publisher 同时写入 List + Pub/Sub，确保迟到客户端能回放历史事件
- Bridge 使用 `ReactiveRedisMessageListenerContainer` 订阅 Pub/Sub，通过 `Flux.create` 桥接为响应式流
- SseEventFactory 统一构建（使用手动 JSON 拼接，建议后续迁移至 ObjectMapper）

---

## 4. 性能分析

### 高优先级

#### 4.1 TTS 使用 CountDownLatch 阻塞线程池线程

**文件**: `TtsService.java` 第 115-137 行

**问题描述**: `synthesize` 方法使用 `CountDownLatch.await()` 同步阻塞调用线程。在 Web 请求（`/tts` 接口）中使用此方法会阻塞 Tomcat 线程池线程，在高并发下可能导致线程耗尽。

```java
CountDownLatch latch = new CountDownLatch(1);
// ...
latch.await();  // 同步阻塞当前线程
```

流式版本 `streamAudio` 使用了响应式 Flux，设计合理，但同步版本存在问题。

**影响范围**: `GET /api/tourist/tts` 接口，高并发时可能导致服务假死。

**建议方案**: 同步 `synthesize` 方法改为异步返回 `CompletableFuture<String>`，或使用 WebFlux 的响应式包装。同时配置 Tomcat 的最大线程数和队列大小。

---

#### 4.2 无 AI 调用限流 ✅ 已修复 (2026-05-12)

**文件**: 所有涉及 AI 调用的服务

**问题描述**: 意图识别、景点问答、闲聊兜底、情感分析、TTS 等多个环节均调用 AI 模型（DashScope），没有任何限流措施。单个游客可通过频繁请求快速耗尽 Token 配额，或在并发高峰时冲击 AI 服务。

**影响范围**: 所有 AI 调用链路，可能导致配额耗尽和服务不稳定。

**建议方案**: 引入 Resilience4j 的 `RateLimiter` 注解，按 `visitorId` 维度限流，或在网关层实现令牌桶限流。

---

#### 4.3 无熔断机制（DashScope/高德 API）

**文件**: 所有外部 API 调用点

**问题描述**: 对 DashScope（LLM、TTS、ASR）和高德地图 API 的调用没有熔断保护。一旦外部服务不可用，异常会直接传播到用户，引发级联失败。

**影响范围**: Graph 执行、语音转文字、语音合成等所有依赖外部 API 的功能。

**建议方案**: 引入 Resilience4j 的 `@CircuitBreaker` 注解，为每个外部 API 调用配置熔断参数（失败率阈值、恢复超时），并提供降级方案（如 AI 服务不可用时返回固定回复）。

---

#### 4.4 Redis ChatMemory TTL 无配置

**文件**: 隐式配置（Spring AI JedisRedisChatMemoryRepository）

**问题描述**: Redis ChatMemory（对话历史存储）的 TTL 未显式配置。Spring AI 的 JedisRedisChatMemoryRepository 默认行为依赖框架实现，可能导致：对话历史无限堆积（Redis 内存压力）或 TTL 过短导致历史信息丢失。

**影响范围**: 多轮对话历史管理，可能影响路线规划等需要上下文的功能。

**建议方案**: 显式配置 Redis ChatMemory 的 TTL（如 30 分钟），在配置类中注入并设置 `spring.ai.chat.memory.repository.redis.ttl`。

---

### 中优先级

#### 4.5 EmotionDetectServiceImpl 中 AI 分析同步阻塞

**文件**: `EmotionDetectServiceImpl.java` 第 116-130 行

**问题描述**: `detectEmotionAsync` 方法虽然标记了 `@Async`，但内部调用 `chatClientBuilder.build().prompt()...call()` 是同步阻塞的。异步注解只保证了方法在独立线程执行，但 AI 调用的阻塞仍然存在，可能导致异步线程池耗尽。

**影响范围**: `POST /api/tourist/stream` 接口，每次对话都会触发情感分析。

**建议方案**: 使用 `CompletableFuture.supplyAsync()` 或 WebClient 的响应式调用，配置独立的 `ThreadPoolTaskExecutor` 给情感分析使用，设置合理核心线程数。

---

#### 4.6 TTS 音频缓存路径写入 /tmp 目录 ✅ 已修复 (2026-05-12)

**文件**: `TtsService.java` 第 47-48 行

**问题描述**: TTS 合成的音频文件写入 `System.getProperty("java.io.tmpdir")`，该目录在不同环境下行为不一致：Linux/macOS 的 `/tmp` 可能在重启后清空，Windows 使用用户临时目录可能跨用户共享。

**影响范围**: `GET /api/tourist/tts/{filename}` 接口，可能出现音频文件丢失。

**建议方案**: 使用配置化的持久化路径（如 OSS、本地存储目录），确保路径可通过 Web 访问。

**修复**: 已通过 `@Value("${jingbanyou.tts.audio-dir:#{null}}")` 注入配置化路径，添加 `@PostConstruct` 启动目录存在性/可写性校验，`application.yml` 配置默认值 `/data/jingbanyou/tts`。

---

## 5. 安全性分析

### 高优先级

#### 5.1 @Anonymous 全开，游客端无访问控制 ✅ 已修复 (2026-05-13)

**原文件**: `TouristController.java`（已删除，拆分为 4 个 Controller，详见 2.1 节）

**问题描述**: 整个 Controller 使用 `@Anonymous` 注解，完全绕过了身份认证和访问控制。游客端 API（流式对话、语音转文字、TTS、会话列表/详情）均无需认证，可能导致：任意用户访问其他游客的会话历史（`GET /conversation/{sessionId}`）、恶意消耗 AI 资源、遍历 sessionId 获取对话数据。

```java
@Anonymous
public class TouristController extends BaseController {
    // 所有方法均无需认证
}
```

**影响范围**: 游客端所有 8 个 API 接口，存在数据泄露和资源滥用风险。

**建议方案**: 移除 `@Anonymous`，接入 JWT 或 Session 认证；对会话详情接口增加 `visitorId` 归属校验；敏感接口增加速率限制。

---

#### 5.2 SSE 事件手动 JSON 拼接，潜在 XSS 风险 ✅ 已修复 (2026-05-12)

**原文件**: `TouristController.java`（已删除）、`StreamingAnswerService.java`

**问题描述**: SSE 事件数据通过 `escapeJson` 手工拼接后发送给前端。虽然 `escapeJson` 处理了部分字符，但遗漏了 `/`（斜杠，用于提前终止标签）和 `\b` 等字符。如果 AI 返回的内容包含恶意脚本片段，通过 SSE 推送到前端可能触发 XSS。

```java
// audioSse 方法中的拼接，遗漏了完整的 JSON 转义
String data = "{\"seq\":" + seq + ",\"chunk\":\"" + base64 + "\",...}";
```

**影响范围**: 所有 SSE 响应事件。

**建议方案**: 统一使用 Jackson `ObjectMapper` 序列化所有 SSE 数据，前端收到数据后进行 DOMPurify 处理。

---

### 中优先级

#### 5.3 sessionId 可预测（雪花 ID）

**文件**: `ChatController.java`

**问题描述**: 当前端不传 `sessionId` 时，系统使用雪花算法生成 ID。虽然雪花 ID 本身难以预测，但如果攻击者知道游客的 visitorId，可以通过遍历雪花 ID 访问会话数据。

```java
String sessionId = request.get("sessionId") instanceof String s && !s.isBlank()
        ? s
        : IdUtil.getSnowflake().nextIdStr();
```

**影响范围**: 会话数据安全。

**建议方案**: 配合认证机制后，使用强随机 UUID 或加密的 sessionId。

---

#### 5.4 TTS 音频路径遍历漏洞风险

**文件**: `VoiceController.java`

**问题描述**: `ttsAudio` 方法直接使用前端传入的 `filename` 拼接文件路径，虽然检查了文件是否存在，但没有校验路径遍历攻击（如 `../../etc/passwd`）。

```java
Path audioPath = Paths.get(System.getProperty("java.io.tmpdir"), "tts", filename);
if (!Files.exists(audioPath)) {
    return ResponseEntity.notFound().build();
}
```

**影响范围**: `GET /api/tourist/tts/{filename}` 接口。

**建议方案**: 对 filename 进行白名单校验（仅允许 `.wav` 后缀），使用 `audioPath.normalize()` 后再检查是否在允许目录下。

---

## 6. 可维护性分析

### 中优先级

#### 6.1 无链路追踪 ID ✅ 已修复 (2026-05-12)

**原文件**: `TouristController.java`（已删除）+ 所有 Service

**问题描述**: 日志中没有 traceId/sessionId 贯穿整个请求链路。在 Graph 执行期间，`executeGraph` 方法打印了 `sessionId`，但后续的 `streamingAnswerService.streamAnswer` 流程中，部分日志可能缺少 traceId，导致分布式环境下排查问题困难。

**建议方案**: 引入 `Tracer`（如 Micrometer / OpenTelemetry）生成 traceId，在 `executeGraph` 入口注入 traceId 到 MDC，确保所有日志输出包含 traceId。

**修复**: 已添加 `micrometer-tracing-bridge-otel` 依赖，创建 `OpenTelemetryConfig` 配置 Tracer Bean，在 `TouristController` 注入 `Tracer` 并在 `chatStream` 入口设置 `MDC.put("traceId", ...)`，`application.yml` 已配置 `management.tracing.sampling.probability=1.0`。

---

#### 6.2 Prompt 分散在配置文件和代码中 ✅ 已修复 (2026-05-12)

**文件**: 多个 Service 和 Graph 节点

**问题描述**: Prompt 分散在以下位置：
- `EmotionDetectServiceImpl` 第 109-130 行（代码中硬编码）
- 各 ChatClient 的系统 prompt（可能在 YAML 配置中）
- Graph 节点的 prompt（在 Node 实现类中）

维护者需要在多个文件中查找和修改 prompt。

**建议方案**: 统一管理 Prompt 至 `prompts/` 目录下的文本文件或 Spring AI 的 PromptTemplate，支持热加载。

**修复**: 已创建 `jingbanyou-tourist/src/main/resources/prompts/` 目录，将情感检测的 system prompt 和 user prompt 模板提取为 `emotion-detect-system-prompt.txt` 和 `emotion-detect-user-prompt-template.txt`，`EmotionDetectServiceImpl` 通过 `@Value` + `Resource` 从 classpath 加载。正负面关键词已迁移到 `application.yml` 的 `jingbanyou.emotion` 配置。

---

#### 6.3 日志级别不规范 ✅ 已修复 (2026-05-12)

**文件**: 多个文件

**问题描述**: 代码中混用了 `log.info`、`log.debug`、`log.warn`，但部分 `log.warn` 实际上应使用 `log.error`（如 AI 服务调用失败、TTS 合成失败），可能导致监控告警遗漏。

```java
// EmotionDetectServiceImpl 第 73 行
log.warn("[情感检测] 检测失败 sessionId={}: {}", sessionId, e.getMessage());

// StreamingAnswerService 第 180 行
log.warn("[流式] TTS 段落失败，跳过: {}", error.getMessage());
```

**建议方案**: 建立日志规范，明确 warn/error 的使用场景。

**修复**:
- `EmotionDetectServiceImpl`: 检测失败 `warn -> error`，Redis 读取失败 `warn -> debug`
- `StreamingAnswerService`: TTS 段落失败 `warn -> error`
- `ChatMemoryService`: 保存元数据失败 `warn -> error`，读取/删除/情感读取/计算时长/清理等 `warn -> debug`
- 已创建 `docs/analysis/tourist-logging-spec.md` 日志规范文档

---

#### 6.4 无 API 文档 ✅ 已修复 (2026-05-12)

**原文件**: `TouristController.java`（已删除）

**原始问题**: Controller 没有 OpenAPI/Swagger 注解，前端和其他调用方无法通过文档了解接口参数和响应格式。

**建议方案**: 添加 SpringDoc `@Operation` 注解，生成 OpenAPI 文档。

**修复**: 已为 4 个 Controller（BootstrapController / ChatController / VoiceController / ConversationController）添加 `@Tag` 类注解和 `@Operation` 端点注解，覆盖全部 10 个端点。

---

## 7. 游客身份与会话管理分析 ✅ 新增 (2026-05-13)

### 7.1 @Anonymous 改造：TouristSessionFilter 会话管理

**改造内容**: 移除 `@Anonymous` 注解，改为通过 `TouristSessionFilter` 拦截 `/tourist/*` 请求，实现无认证的游客会话管理。

**变更要点**:
- `@VisitorToken` 注解（已删除）和 `VisitorTokenFilter`（已删除）不再使用
- HMAC token 机制已移除（原 token-secret 暴露在浏览器 JS 中存在安全隐患）
- 游客端不再需要任何注解标记，直接通过 Filter 统一处理会话

**新增文件**:

| 文件 | 位置 | 作用 |
|------|------|------|
| `VisitorSessionDTO.java` | `jingbanyou-common/.../common/core/domain/` | 游客会话数据对象，存储 sceneId、entranceId、firstVisitTime、lastActiveTime |
| `TouristSessionService.java` | `jingbanyou-common/.../common/core/service/` | 核心服务：getOrCreateSession() 校验或创建会话，TTL=2小时 |
| `TouristSessionFilter.java` | `jingbanyou-framework/.../framework/filter/` | Servlet Filter，拦截 /tourist/* 请求，自动提取 visitorId 处理会话与心跳 |

**删改文件**:
- 删除 `VisitorToken.java` 注解文件
- 删除 `VisitorTokenFilter.java` 过滤器文件
- 移除 `FilterConfig.java` 中 `visitorTokenFilterRegistration()` Bean
- 从两个 `application.yml` 中移除 `token-secret` 配置
- 从 `TouristController.java`（已删除）移除 `@VisitorToken` 注解

**新增配置**: `TouristSessionFilter` 在 `FilterConfig.java` 中注册，URL 模式 `/tourist/*`。

### 7.2 visitorId 的实际作用

visitorId 是**会话标识符**，本质是**游客匿名会员卡号**（前端 UUID），用于：

| 功能 | 实现方式 | 说明 |
|------|---------|------|
| **会话隔离** | sessionId（雪花算法） | ChatMemory 的 Redis key = sessionId，**真正隔离多轮对话** |
| **会话归属校验** | visitorId | endChat / getConversationDetail 时校验，只能查看自己的会话 |
| **会话续期** | Redis TTL=2h | 活跃游客自动续期，不活跃 2 小时后过期 |
| **游客行为统计** | Redis key 前缀 | 可统计 UV、峰值时段、会话时长 |

**注意**: visitorId **不防伪造**（任何人都可以传任意 UUID），它是匿名标识符，不是认证凭证。真正识别用户需要登录绑定手机号。

### 7.3 sessionId vs visitorId 的区别

| 字段 | 作用 | 生成方式 |
|------|------|---------|
| **visitorId** | 游客唯一标识（匿名） | 前端 UUID，本地存储 |
| **sessionId** | 对话窗口唯一标识 | 雪花算法（前端或后端生成） |

- 一个 visitorId 可以有多个 sessionId（游客开了多个聊天窗口）
- 一个 sessionId 只属于一个 visitorId
- ChatMemory 的 key 是 sessionId，**不同游客的对话完全隔离**

### 7.4 SecurityConfig 放行游客端路径 ✅ 已修复 (2026-05-13)

**问题**: Spring Security `.anyRequest().authenticated()` 会拦截所有 `/tourist/*` 请求。`PermitAllUrlProperties` 只扫描 `@Anonymous` 注解，游客端未标注任何认证注解，不在白名单中。

**修复**: 在 `SecurityConfig` 中显式放行：

```java
.authorizeHttpRequests((requests) -> {
    // ...
    // 游客端接口放行，由 TouristSessionFilter 统一处理会话
    .requestMatchers("/tourist/**").permitAll()
    .anyRequest().authenticated();
})
```

**后续变更**: 原 `@VisitorToken` + `VisitorTokenFilter` 的 HMAC token 方案已删除（2026-05-13），HMAC secret 在浏览器端暴露存在安全隐患。

### 7.5 完整请求链路

```
请求
  ↓
TouristRateLimitFilter (/tourist/*) — 限流检查（按 visitorId/IP）
  ↓
TouristSessionFilter (/tourist/*) — 提取 visitorId → Redis 创建/续期会话 + ZSet 在线心跳
  ↓
Spring Security: .requestMatchers("/tourist/**").permitAll() — 直接放行
  ↓
DispatcherServlet → TouristController
```

### 7.7 实时在线人数统计 ✅ 新增 (2026-05-13)

**方案**: Redis Sorted Set + heartbeat

在 `TouristSessionFilter` 中，每次请求过来先 `getOrCreateSession()`，再 `heartbeat()`：

```java
// Redis ZSet: visitor:online:{sceneId}
// member = visitorId, score = 当前时间戳
redisCache.zAdd("visitor:online:" + sceneId, visitorId, System.currentTimeMillis());

// 查询在线人数 = 过去2小时内有心跳的游客
redisCache.zCount("visitor:online:" + sceneId, now - 2h, now);
```

**接入点**:

| 接口 | 字段 |
|------|------|
| `GET /manage/stats/today-overview` | `OperationOverviewVO.onlineVisitors`（管理端数据大屏） |
| `GET /tourist/bootstrap` | `data.onlineCount`（游客端首屏） |

**特点**:
- 每个请求都刷新时间戳，2小时无心跳自动掉出窗口
- 零额外接口，Filter 中一行 `heartbeat()` 搞定
- 可扩展每日 UV（`zCard` 取集合大小）

### 7.8 @Anonymous 放行与游客身份处理

`@Anonymous` 仍用于登录、注册、验证码等完全公开接口。`/tourist/**` 路径在 `SecurityConfig` 中通过 `.requestMatchers("/tourist/**").permitAll()` 直接放行，游客身份由 `TouristSessionFilter` 在后端透明处理。

---

## 8. 改进建议汇总表

| # | 问题 | 严重度 | 影响范围 | 建议方案 | 工作量 |
|---|------|--------|----------|----------|--------|
| 1 | Controller 依赖注入过多（11个） | 高 | TouristController | ✅ 已拆分为 4 个聚焦 Controller（Bootstrap/Chat/Voice/Conversation），删除 TouristController 和 TouristFacadeService，详见 2.1、3.5 节 | 已完成 |
| 2 | streamAnswer 12个参数 ✅ 已完成 (2026-05-13) | 高 | StreamingAnswerService | 已创建 StreamAnswerContext DTO（Java record，12 字段）封装参数 | 已完成 |
| 3 | 手动 JSON 字符串拼接 SSE 🔶 部分修复 | 高 | TouristController + StreamingAnswerService | SseEventFactory 已创建但使用手动 JSON 拼接（非 ObjectMapper），详见 3.7 节 | 小 |
| 4 | escapeJson 代码重复 | 高 | TouristController + StreamingAnswerService | ✅ 已由 SseEventFactory 统一构建，详见 3.7 节 | 已完成 |
| 5 | @Anonymous 全开无认证 | 高 | 游客端全部 8 个 API | 已改造为 @VisitorToken + visitorId 校验体系，详见本文档第 7 节新增分析 | 已完成 |
| 6 | TTS CountDownLatch 阻塞线程池 🔶 部分修复 (2026-05-13) | 高 | GET /tts 接口 | 已改为 `CompletableFuture<String>` + `@CircuitBreaker(name="dashscope-tts")` + fallback，但内部 `latch.await()` 仍在 `supplyAsync` lambda 中阻塞 ForkJoinPool 线程 | 小 |
| 7 | syncToMySQL 方法过长（106行） ✅ 已完成 (2026-05-13) | 中 | ChatMemoryService | 已拆分为 7 个私有方法：loadMessages / persistInteractions / extractFirstMessage / extractLastMessage / loadEmotionData / calculateDuration / persistConversation | 已完成 |
| 8 | buildRouteSummary/AttachmentsJson 重复 ✅ 已完成 (2026-05-13) | 中 | TouristController + StreamingAnswerService | 已抽取到 RouteAttachmentBuilder.java 统一处理 | 已完成 |
| 9 | 无统一错误码 ✅ 已完成 (2026-05-13) | 中 | 游客端全部 API | 已在 jingbanyou-common 创建 TouristErrorCode 枚举 | 已完成 |
| 10 | MultipartFile 无大小限制 ✅ 已完成 (2026-05-12) | 中 | POST /voice/transcribe | application.yml 已配置 max-file-size=10MB / max-request-size=20MB | 已完成 |
| 11 | 无 AI 调用限流 ✅ 已完成 (2026-05-13) | 高 | 所有 AI 调用链路 | TouristRateLimitFilter + Redis Lua 脚本，按 visitorId/IP 限流 | 已完成 |
| 12 | 无熔断机制 ✅ 已完成 (2026-05-13) | 高 | 所有外部 API 调用 | 4 个外部 API 均已有 @CircuitBreaker：dashscope-tts / dashscope-asr / dashscope-llm / amap-route，各有 fallback 降级方法 | 已完成 |
| 13 | Redis ChatMemory TTL 无配置 ✅ 已完成 (2026-05-13) | 中 | 多轮对话历史 | admin: spring.ai.dashscope.chat.memory.repository.redis.time-to-live=24h, tourist: jingbanyou.tourist.chat-memory.ttl-minutes=30 | 已完成 |
| 14 | 情感分析同步阻塞（@Async 内部） ✅ 已完成 (2026-05-13) | 中 | POST /stream 接口 | 已改为 CompletableFuture.runAsync + 专用 emotionDetectionExecutor 线程池 | 已完成 |
| 15 | SSE 手动 JSON 拼接 XSS 风险 🔶 部分修复 | 高 | 所有 SSE 响应 | SseEventFactory 已创建但使用手动 JSON 拼接（非 ObjectMapper），详见 3.7 节 | 小 |
| 16 | TTS 音频路径遍历漏洞 ✅ 已完成 (2026-05-13) | 中 | GET /tts/{filename} | 三层防护：.wav 白名单校验 + / \ 路径分隔符检查 + canonical path startsWith 验证 | 已完成 |
| 17 | ChatMemoryService 混合 Redis/MySQL ✅ 已完成 (2026-05-13) | 中 | ChatMemoryService | 已拆分为 ChatMetadataServiceImpl（Redis）+ ConversationPersistenceServiceImpl（MySQL），原 ChatMemoryService 标注 @Deprecated | 已完成 |
| 18 | 无链路追踪 ID ✅ 已完成 (2026-05-13) | 中 | 全部日志 | 已引入 Micrometer Tracer + MDC traceId，TouristController 注入 Tracer | 已完成 |
| 19 | Prompt 分散在代码和配置 ✅ 已完成 (2026-05-12) | 中 | 多文件 | 已迁移到 prompts/ 目录（emotion-detect-*.txt），关键词迁移到 application.yml | 已完成 |
| 20 | HybridRetrievalNode / MapRouteApiInvokerNode 捕获过宽 | 低 | Graph 节点 | 分类捕获具体异常类型 | 小 |

---

## 9. 优先修复建议

### 第一阶段（高优先级，安全和性能）
1. ~~**移除 @Anonymous**~~ → ✅ 已完成：改用 `TouristSessionFilter` + visitorId 会话管理，详见第 7 节
2. **引入熔断和限流**：✅ 熔断已完成（4 个外部 API 全覆盖），✅ 限流已完成（TouristRateLimitFilter + Redis Lua）
3. **修复 TTS 同步阻塞**：将 `synthesize` 方法改为异步
4. ~~**统一 JSON 序列化**~~ → ✅ 已完成：SseEventFactory + ObjectMapper，详见 3.7 节

### 第二阶段（代码质量重构）
5. ~~**拆分 TouristController**~~ → ✅ 已完成：按领域拆分为 4 个聚焦 Controller（Bootstrap/Chat/Voice/Conversation），删除 TouristFacadeService，详见 2.1、3.5 节
6. ~~**封装 streamAnswer 参数**~~ → ✅ 已完成：StreamAnswerContext DTO
7. ~~**提取 escapeJson 工具类**~~ → ✅ 已完成：SseEventFactory 统一构建，详见 3.7 节

### 第三阶段（架构优化）
8. ~~**拆分 ChatMemoryService**~~ → ✅ 已完成：ChatMetadataServiceImpl + ConversationPersistenceServiceImpl，原 ChatMemoryService 标注 @Deprecated
9. ~~**引入链路追踪**~~ → ✅ 已完成：Micrometer Tracer + MDC traceId
10. ~~**统一 Prompt 管理**~~ → ✅ 已完成：prompts/ 目录 + application.yml 配置
11. **引入消息队列解耦** → ✅ 已完成 (2026-05-13)：RabbitMQ Topic Exchange + 死信队列，详细分析见 3.6 节

### 第四阶段（异步化）✅ 已完成

RabbitMQ 异步对话体系 和 SSE 流桥接体系 均已实施，详细架构设计和组件清单参见：
- **3.6 节 RabbitMQ 异步对话体系**：ChatRequestMessage、RabbitMQConfig、ChatRequestConsumer
- **3.7 节 SSE 流桥接体系**：SseResultPublisher、SseStreamBridge、SseEventFactory、SseMessage

新增端点：`POST /tourist/chat`（提交请求）+ `GET /tourist/stream/{conversationId}`（SSE 订阅结果）
