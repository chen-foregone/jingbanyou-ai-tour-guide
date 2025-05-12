# 景伴游项目改造实现学习指南

> 创建日期：2026-05-12
> 适用人群：Spring Boot 后端开发者
> 前置知识：Spring Boot 3.x、MyBatis-Plus、Redis、Spring Security

---

## 目录

1. [Resilience4j 熔断保护外部 API](#1-resilience4j-熔断保护外部-api)
2. [CountDownLatch → CompletableFuture 异步化](#2-countdownlatch--completablefuture-异步化)
3. [控制器瘦身：多控制器拆分 + SseEventFactory](#3-控制器瘦身多控制器拆分--sseeventfactory)
4. [长方法拆分与单一职责重构](#4-长方法拆分与单一职责重构)
5. [统一错误码体系](#5-统一错误码体系)
6. [参数过多 → DTO/Record 封装](#6-参数过多--dtorecord-封装)
7. [接口与实现分离（依赖倒置）](#7-接口与实现分离依赖倒置)
8. [Graph State Key 常量化](#8-graph-state-key-常量化)
9. [日志规范与链路追踪](#9-日志规范与链路追踪)
10. [路径遍历漏洞修复](#10-路径遍历漏洞修复)
11. [RabbitMQ 异步对话模式](#11-rabbitmq-异步对话模式)
12. [SSE 流桥接（Redis Pub/Sub + List）](#12-sse-流桥接redis-pubsub--list)
13. [游客会话管理（visitorId + 在线人数统计）](#13-游客会话管理visitorid--在线人数统计)
14. [高优先级安全修复（isAdmin / 异常泄露 / JWT密钥）](#14-高优先级安全修复isadmin--异常泄露--jwt密钥)

---

<!-- Section 1 (VisitorToken pattern) removed 2026-05-13 → TouristSessionFilter is now the sole mechanism -->

## 1. Resilience4j 熔断保护外部 API

### 问题场景

AI 服务（DashScope）和地图服务（高德）是外部依赖。一旦宕机，异常会直接抛给用户，可能引发级联故障。

### 核心概念

```
             正常状态
           ↙        ↘
    失败率 < 50%    失败率 >= 50%
      ↓                  ↓
    CLOSED ──────→ OPEN（快速失败）
      ↑               ↓ 等待 60s
      └── HALF_OPEN ←┘
        （试探性放行少数请求）
```

### Step 1：添加依赖

```xml
<!-- jingbanyou-tourist/pom.xml -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
```

### Step 2：配置熔断参数

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10          # 统计窗口：最近10次调用
        minimumNumberOfCalls: 5        # 最少统计5次才熔断
        failureRateThreshold: 50       # 失败率超过50%触发熔断
        waitDurationInOpenState: 60s   # 熔断后60秒进入半开
        permittedNumberOfCallsInHalfOpenState: 3  # 半开状态最多放行3个请求
    instances:
      dashscope-llm:
        baseConfig: default
      dashscope-tts:
        baseConfig: default
        waitDurationInOpenState: 30s   # TTS 恢复更快
      amap-route:
        baseConfig: default
```

### Step 3：在方法上添加熔断注解

```java
// TtsService.java

@CircuitBreaker(name = "dashscope-tts", fallbackMethod = "synthesizeFallback")
public CompletableFuture<String> synthesize(String text, DigitalHumanConfig digitalHuman) {
    return CompletableFuture.supplyAsync(() -> {
        // ... 调用 DashScope TTS API ...
        return audioFilePath;
    });
}

// fallback 方法签名必须和原方法一致（返回类型、参数类型、参数个数）
private CompletableFuture<String> synthesizeFallback(
        String text, DigitalHumanConfig digitalHuman, Throwable t) {
    log.error("[TTS] 熔断降级: {}", t.getMessage());
    return CompletableFuture.completedFuture(null);  // 返回空，前端显示降级提示
}
```

**关键点：**
- `fallbackMethod` 必须是同一类中的 `public` 或 `private` 方法
- fallback 方法签名 = 原方法参数 + 末尾增加 `Throwable` 参数
- 返回类型必须兼容（如果是 void，fallback 也必须是 void）
- 每个外部服务独立熔断实例，互不影响

### Step 4：在 Graph 节点中使用

```java
// MapRouteApiInvokerNode.java

@CircuitBreaker(name = "amap-route", fallbackMethod = "applyFallback")
public OverAllState apply(OverAllState state) {
    // ... 调用高德 API ...
    return state;
}

// fallback：返回降级引导语
private OverAllState applyFallback(OverAllState state, Throwable t) {
    log.error("[高德路线] 熔断降级: {}", t.getMessage());
    state.put(GraphStateKey.ROUTE_STATUS, "error");
    state.put(GraphStateKey.GUIDE_MESSAGE, "路线服务暂时不可用，请稍后再试");
    return state;
}
```

**关键点：**
- 熔断不破坏 Graph 状态机的流程，只是让节点返回一个"软失败"状态
- 下游节点可以根据状态 key 走不同的处理路径

---

## 2. CountDownLatch → CompletableFuture 异步化

### 问题场景

原 TTS 合成接口使用 `CountDownLatch.await()` 同步阻塞 Tomcat 线程：

```java
// ❌ 原代码
CountDownLatch latch = new CountDownLatch(1);
synthesizer.call(text)
    .onCompleted(() -> latch.countDown());
latch.await();  // 阻塞 Tomcat 线程！
return filePath;
```

高并发时，200 个 Tomcat 线程全部阻塞在 `await()`，新请求无法处理。

### 重构为异步

```java
// ✅ 重构后
public CompletableFuture<String> synthesize(String text, DigitalHumanConfig digitalHuman) {
    CompletableFuture<String> future = new CompletableFuture<>();

    SpeechSynthesizer synthesizer = buildSynthesizer(digitalHuman);

    synthesizer.call(text);
    synthesizer.setOnCompleted(event -> {
        // ... 处理音频流，写入文件 ...
        synthesizer.close();
        future.complete(audioFilePath);  // ← 非阻塞完成
    });
    synthesizer.setOnError(error -> {
        synthesizer.close();
        future.completeExceptionally(new RuntimeException(error.getMessage()));
    });

    return future;  // ← 立即返回，不阻塞
}
```

**关键点：**
- `CompletableFuture.complete()` 不阻塞当前线程
- 调用方可以通过 `.thenAccept()` / `.whenComplete()` 注册回调
- 也可以 `.get()` 同步等待（兼容旧代码）

### Controller 层异步返回

```java
// TouristController.java - 方式一：DeferredResult
@GetMapping("/tts")
public DeferredResult<AjaxResult> tts(@RequestParam String text,
                                       @RequestParam Long humanId) {
    DeferredResult<AjaxResult> deferred = new DeferredResult<>(30000L); // 30s 超时

    ttsService.synthesize(text, digitalHuman)
        .whenComplete((path, ex) -> {
            if (ex != null) {
                deferred.setResult(error(TouristErrorCode.T009, ex.getMessage()));
            } else {
                deferred.setResult(success(path));
            }
        });

    return deferred;  // Tomcat 线程立即释放
}

// 方式二：WebFlux 响应式（如果项目已引入 WebFlux）
@GetMapping("/tts")
public Mono<AjaxResult> tts(@RequestParam String text) {
    return Mono.fromFuture(ttsService.synthesize(text, digitalHuman))
               .map(path -> success(path))
               .onErrorResume(e -> Mono.just(error(TouristErrorCode.T009)));
}
```

**关键点：**
- `DeferredResult` 是 Servlet 容器（Tomcat）的异步方案
- `Mono` 是 WebFlux 的响应式方案，需要项目引入 spring-boot-starter-webflux
- 设置超时时间防止请求永远挂起

---

## 3. 控制器瘦身：多控制器拆分 + SseEventFactory

> **注意**：FacadeService 模式已被替换。当前方案改为按领域拆分为 4 个聚焦的 Controller（BootstrapController、ChatController、VoiceController、ConversationController），每个 Controller 将业务逻辑内联为 private 方法，不再使用独立的 FacadeService 接口和实现类。以下保留的 FacadeService 内容供参考 SseEventFactory 抽取思路。

### 问题场景

原 `TouristController` 520+ 行，注入 11 个依赖。同时承担了 Graph 编排、SSE 构建、参数校验、业务调度多种职责。

### 重构三步法

#### 第一步：抽取 FacadeService

把所有**业务编排逻辑**从 Controller 移入 FacadeService：

```java
// ITouristFacadeService.java
public interface ITouristFacadeService {
    BootstrapResult bootstrap(Long scenicId);
    OverAllState executeGraph(String message, String sessionId, Long scenicId, String visitorId);
    void endChat(Map<String, Object> request);
    String transcribe(MultipartFile file, String language);
    CompletableFuture<String> tts(String text, Long humanId);
    List<VisitorConversation> getConversations(String visitorId, Long scenicId);
    VisitorConversation getConversationDetail(String sessionId, String visitorId);
}

// TouristFacadeServiceImpl.java
@Service
@RequiredArgsConstructor
public class TouristFacadeServiceImpl implements ITouristFacadeService {

    // 所有11个依赖注入到这里
    private final IScenicAreaService scenicAreaService;
    private final IDigitalHumanConfigService digitalHumanConfigService;
    // ... 其余9个 ...

    @Override
    public OverAllState executeGraph(String message, String sessionId,
                                      Long scenicId, String visitorId) {
        Map<String, Object> initState = new HashMap<>();
        initState.put(GraphStateKey.SESSION_ID, sessionId);
        initState.put(GraphStateKey.QUESTION, message);
        initState.put(GraphStateKey.SCENIC_ID, scenicId);
        initState.put(GraphStateKey.VISITOR_ID, visitorId);

        return streamGraphConfiguration.execute(initState);
    }
    // ...
}
```

#### 第二步：抽取 SseEventFactory

把所有 **SSE 事件构建**逻辑独立成类：

```java
// SseEventFactory.java
@Component
public class SseEventFactory {

    public ServerSentEvent<String> answer(String content) {
        String data = JsonEscapeUtil.toJson(Map.of(
            "type", "answer",
            "content", content
        ));
        return ServerSentEvent.<String>builder()
                .event("answer")
                .data(data)
                .build();
    }

    public ServerSentEvent<String> error(TouristErrorCode code, String detail) {
        String data = JsonEscapeUtil.toJson(Map.of(
            "type", "error",
            "code", code.getCode(),
            "msg", code.format(detail)
        ));
        return ServerSentEvent.<String>builder()
                .event("error")
                .data(data)
                .build();
    }

    // audio, metadata, done, answerFragment 等方法同理...
}
```

**关键点：**
- 使用 `JsonEscapeUtil.toJson()`（底层是 Jackson `ObjectMapper`）代替手写 JSON 拼接
- 返回值统一使用 Spring 的 `ServerSentEvent<T>` 而非字符串
- `@Component` 使其成为 Spring Bean，可在任何地方注入

#### 第三步：Controller 瘦身

```java
// TouristController.java - 瘦身后
@Tag(name = "游客端")
@RestController
@RequestMapping("/api/tourist")
@RequiredArgsConstructor
public class TouristController extends BaseController {

    // 仅3个依赖
    private final ITouristFacadeService facadeService;
    private final IStreamingAnswerService streamingAnswerService;
    private final SseEventFactory sseEventFactory;

    @Operation(summary = "流式对话（返回SSE）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody Map<String, Object> request) {
        // 参数校验
        Long scenicId = parseScenicId(request);
        String message = parseMessage(request);

        // 委托 Facade
        OverAllState state = facadeService.executeGraph(message, sessionId, scenicId, visitorId);

        // 委托 SSE Factory + StreamingAnswerService
        StreamAnswerContext ctx = new StreamAnswerContext(/* ... */);
        return streamingAnswerService.streamAnswer(ctx);
    }

    @Operation(summary = "获取会话详情")
    @GetMapping("/conversation/{sessionId}")
    public AjaxResult getConversationDetail(@PathVariable String sessionId) {
        String visitorId = VisitorContext.getVisitorId();
        VisitorConversation conv = facadeService.getConversationDetail(sessionId, visitorId);

        // ownership check
        if (conv == null || !visitorId.equals(conv.getVisitorId())) {
            return error(TouristErrorCode.T011);
        }
        return success(conv);
    }
}
```

**效果对比：**

| 指标 | 重构前 | 重构后 |
|------|--------|--------|
| Controller 行数 | 520+ | ~200 |
| 依赖注入数 | 11 | 3 |
| 职责 | Graph+SSE+业务+校验 | 仅参数校验+委托 |
| 可测试性 | 难以 Mock 11 个依赖 | 3 个 Mock，清晰 |

---

## 4. 长方法拆分与单一职责重构

### 问题场景

`ChatMemoryService.syncToMySQL()` 原 106 行，承担了 6 个职责。

### 拆分原则

1. **每个私有方法只做一件事**
2. **提取的粒度：一次方法调用不超过一屏（~30行）**
3. **原来的公共方法只负责编排流程**

### 拆分示例

```java
// ✅ 重构后：公共方法只做编排
@Override
public void syncToMySQL(String sessionId, Long scenicId, Long humanId, String visitorId) {
    try {
        // 1. 从 Redis 加载消息
        List<Message> messages = loadMessages(sessionId);

        // 2. 持久化交互记录
        persistInteractions(sessionId, scenicId, humanId, visitorId, messages);

        // 3. 加载情感数据
        Map<String, Object> emotionData = loadEmotionData(sessionId);

        // 4. 计算会话时长
        DurationResult duration = calculateDuration(sessionId, messages);

        // 5. 持久化会话记录
        persistConversation(sessionId, scenicId, humanId, visitorId,
                messages, emotionData, duration);

        // 6. 清理 Redis
        clearRedisData(sessionId);

        log.info("[会话同步] 完成 sessionId={}", sessionId);
    } catch (Exception e) {
        log.error("[会话同步] 失败 sessionId={}", sessionId, e);
    }
}

// 拆分后的小方法 — 每个 < 30 行
private List<Message> loadMessages(String sessionId) {
    List<Message> messages = chatClientMemory.get(sessionId, Integer.MAX_VALUE);
    if (messages == null || messages.isEmpty()) {
        throw new ServiceException("会话无消息");
    }
    return messages;
}

private DurationResult calculateDuration(String sessionId, List<Message> messages) {
    if (messages.isEmpty()) return DurationResult.ZERO;

    // 用雪花ID反推时间戳
    long startTime = SnowflakeIdUtil.extractTimestamp(sessionId);
    long endTime = System.currentTimeMillis();
    long durationMs = endTime - startTime;

    return new DurationResult(startTime, endTime, durationMs);
}

// ...其余小方法同理...
```

### 提取雪花ID工具方法

```java
// jingbanyou-common/src/main/java/.../utils/SnowflakeIdUtil.java
public class SnowflakeIdUtil {
    /**
     * 从雪花算法ID中提取时间戳
     * 雪花ID结构: [41位时间戳][10位机器ID][12位序列号]
     */
    public static long extractTimestamp(String snowflakeId) {
        long id = Long.parseLong(snowflakeId);
        // 右移22位，去掉低位的机器ID和序列号
        return (id >> 22) + 1288834974657L;  // Twitter 纪元
    }
}
```

**关键点：**
- 原方法从 106 行缩减到 ~20 行（只做编排）
- 每个小方法可独立单元测试
- 雪花ID的反推逻辑被封在工具类中，调用方无需关心

---

## 5. 统一错误码体系

### 问题场景

原代码中错误信息分散在代码各处，如 `error("景区ID不能为空")`。前端无法根据错误码做差异化处理。

### 实现

```java
// jingbanyou-common/src/main/java/.../enums/TouristErrorCode.java
public enum TouristErrorCode {
    T001("T001", "景区ID不能为空"),
    T002("T002", "景区不存在"),
    T003("T003", "会话不存在"),
    T004("T004", "sessionId不能为空"),
    T005("T005", "visitorId不能为空"),
    T006("T006", "消息内容不能为空"),
    T007("T007", "音频文件不能为空"),
    T008("T008", "语音识别失败"),
    T009("T009", "语音合成失败: %s"),
    T010("T010", "文本不能为空"),
    T011("T011", "无权限访问此会话"),
    T500("T500", "系统处理失败: %s");

    private final String code;
    private final String messageTemplate;

    TouristErrorCode(String code, String messageTemplate) {
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    public String getCode() { return code; }

    /** 格式化错误消息（支持 %s 占位符） */
    public String format(Object... args) {
        return args.length == 0 ? messageTemplate
                : String.format(messageTemplate, args);
    }
}
```

### 扩展 BaseController

```java
// BaseController.java - 新增重载方法
protected AjaxResult error(TouristErrorCode errorCode, Object... args) {
    return AjaxResult.error(errorCode.getCode(), errorCode.format(args));
}
```

### 使用

```java
// 替换前
return error("景区ID不能为空");
return error("景区不存在");

// 替换后
return error(TouristErrorCode.T001);
return error(TouristErrorCode.T002);
return error(TouristErrorCode.T009, exceptionMessage);  // 带参数的
```

**关键点：**
- 枚举天然是单例，按领域分组管理
- 每个错误码有唯一标识，便于监控统计
- `format()` 支持参数化，比硬拼接更安全
- 客户端可以 `switch(code)` 做差异化 UI 处理

---

## 6. 参数过多 → DTO/Record 封装

### 问题场景

`streamAnswer` 有 12 个参数，违反"方法参数不超过 5 个"的最佳实践。

### Java 17 Record 方案

```java
// jingbanyou-tourist/src/main/java/.../dto/StreamAnswerContext.java

/**
 * 流式回答上下文
 *
 * 将 streamAnswer 的12个参数封装为单一对象
 */
public record StreamAnswerContext(
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
    long startTimestamp
) {
    /** 快捷构造器，默认时间戳为当前时间 */
    public static StreamAnswerContext of(String sessionId, String userMessage, /*...*/) {
        return new StreamAnswerContext(sessionId, /*...*/, System.currentTimeMillis());
    }
}
```

### 为什么用 Record 而不是 class？

| 特性 | class | record |
|------|-------|--------|
| getter | 手写 `getXxx()` | 自动生成 `xxx()` |
| 不可变性 | 需手动 final | 自动不可变 |
| equals/hashCode | 需手动 | 自动基于所有字段 |
| toString | 需手动 | 自动生成 |
| 行数 | ~60 行 | ~15 行 |

### 使用

```java
// 调用方
StreamAnswerContext ctx = new StreamAnswerContext(
    sessionId, scenicId, humanId, visitorId,
    intent, message, retrievedDocs,
    digitalHuman, rawRoutes, intentType,
    graphCostMs, startTimestamp
);
return streamingAnswerService.streamAnswer(ctx);

// 被调用方
public Flux<ServerSentEvent<String>> streamAnswer(StreamAnswerContext ctx) {
    log.info("[流式] sessionId={} intent={}", ctx.sessionId(), ctx.intent());
    // 访问: ctx.sessionId(), ctx.scenicId(), ...
}
```

**关键点：**
- Record 是 Java 17 标准特性，无需 Lombok
- 所有字段自动 final，线程安全
- 如果添加新字段，只需修改 Record 定义，所有调用方编译期报错（不会遗漏）

---

## 7. 接口与实现分离（依赖倒置）

### 问题场景

`ITtsService` 接口定义在 `jingbanyou-tourist` 模块，实现在同一模块。如果将来要切换到另一个 TTS 供应商（如从 DashScope 切换到讯飞），需要修改 tourist 模块代码。

### 解决方案

```
jingbanyou-common/
  └── service/
        ├── ITtsService.java          ← 接口定义在这里
        └── ITranscribeService.java

jingbanyou-tourist/
  └── service/impl/
        ├── DashScopeTtsService.java   ← 实现 ①
        └── DashScopeTranscribeService.java

jingbanyou-tourist-v2/  (未来)
  └── service/impl/
        ├── XunfeiTtsService.java      ← 实现 ②
        └── XunfeiTranscribeService.java
```

### 具体实现

```java
// jingbanyou-common/src/main/java/.../service/ITtsService.java
public interface ITtsService {
    CompletableFuture<String> synthesize(String text, DigitalHumanConfig config);
    Flux<ServerSentEvent<String>> streamAudio(String text, DigitalHumanConfig config);
    Path getAudioDir();
}

// jingbanyou-tourist/src/main/java/.../service/impl/TtsService.java
@Service
public class TtsService implements ITtsService {  // 实现 common 的接口
    // ...
}
```

### 更新所有 import

```java
// 修改前（自己模块的接口）
import cn.edu.gdou.jingbanyou.tourist.service.ITtsService;

// 修改后（common 模块的接口）
import cn.edu.gdou.jingbanyou.common.service.ITtsService;
```

**关键点：**
- 依赖倒置原则：高层模块（Controller）依赖抽象（common 接口），不依赖具体实现（tourist impl）
- Maven 依赖顺序：common 不能依赖 tourist，但 tourist 可以依赖 common
- 如果 common 接口引用了 manage 模块的类（如 DigitalHumanConfig），需要重构：把配置类也提取到 common，或者接口接受泛型/MAP 参数

---

## 8. Graph State Key 常量化

### 问题场景

Graph 状态机各节点通过 `Map<String, Object>` 传递数据，key 是字符串硬编码：

```java
// ❌ 分散在各处
state.put("retrieved_docs", docs);
String docs = (String) state.get("retrieved_docs");
state.put("route_status", "ok");
```

如果写错 key 名（如 `retreived_docs` 少拼一个 i），编译器不报错，运行时才发现。

### 解决方案

```java
// jingbanyou-tourist/src/main/java/.../constant/GraphStateKey.java
public final class GraphStateKey {
    private GraphStateKey() {}  // 工具类，禁止实例化

    public static final String SESSION_ID = "sessionId";
    public static final String QUESTION = "question";
    public static final String SCENIC_ID = "scenicId";
    public static final String VISITOR_ID = "visitorId";
    public static final String INTENT = "intent";
    public static final String INTENT_TYPE = "intentType";
    public static final String RETRIEVED_DOCS = "retrievedDocs";
    public static final String ANSWER = "answer";
    public static final String ROUTE_STATUS = "routeStatus";
    public static final String RAW_ROUTES = "rawRoutes";
    public static final String POLISHED_ROUTES = "polishedRoutes";
    public static final String GUIDE_MESSAGE = "guideMessage";
    public static final String TOKENS_USED = "tokensUsed";
    public static final String MODEL_USED = "modelUsed";
    public static final String PROFILE = "profile";
}
```

### 使用

```java
// ✅ 常量化后
state.put(GraphStateKey.RETRIEVED_DOCS, docs);
String docs = (String) state.get(GraphStateKey.RETRIEVED_DOCS);

// HybridRetrievalNode
state.put(GraphStateKey.RETRIEVED_DOCS, retrievedContent);
state.put(GraphStateKey.TOKENS_USED, tokensUsed);
```

**关键点：**
- `final class` + `private constructor` 确保是纯常量类
- 常量名与 key 值一致，方便全局搜索
- IDE 自动补全 → 不会拼错
- 如果删除某个 key，IDE 会报出所有引用处
- 如果重构 key 名，只需改一处

---

## 9. 日志规范与链路追踪

### 日志级别规范

```markdown
# tourist-logging-spec.md

| 级别  | 使用场景                                | 示例                                      |
|-------|----------------------------------------|------------------------------------------|
| ERROR | AI 服务不可用、数据持久化失败、安全异常    | "TTS合成失败"、"MySQL同步异常"            |
| WARN  | 降级路径触发、超时、参数校验失败           | "熔断器打开，使用降级响应"                 |
| INFO  | 请求开始/结束、Graph 执行耗时、同步完成    | "[流式] 开始,sessionId=xxx"              |
| DEBUG | 缓存命中/未命中、详细参数、中间状态        | "Redis 命中,key=chat:xxx"               |
```

### 日志修正示例

```java
// ❌ 修正前
log.warn("[情感检测] 检测失败 sessionId={}: {}", sessionId, e.getMessage());
log.warn("[流式] TTS 段落失败，跳过: {}", error.getMessage());
log.warn("[ChatMemory] 读取情感数据失败: {}", e.getMessage());

// ✅ 修正后
log.error("[情感检测] 检测失败 sessionId={}", sessionId, e);   // AI 服务失败是严重问题
log.error("[流式] TTS 段落失败: {}", error.getMessage());       // TTS 失败影响用户体验
log.debug("[ChatMemory] 读取情感数据失败: {}", e.getMessage());  // Redis miss 是正常降级
```

**关键点：**
- ERROR 和 WARN 会上报到监控系统（如 Prometheus Alertmanager）
- 把降级路径的正常行为从 WARN 降为 DEBUG，减少告警噪音
- 把真正的服务故障从 WARN 升为 ERROR，确保能被监控捕获在`

### 链路追踪

```java
// TouristController.java
private final Tracer tracer;

@PostMapping("/stream")
public Flux<ServerSentEvent<String>> chatStream(@RequestBody Map<String, Object> request) {
    // 注入 traceId
    String traceId = tracer.currentSpan().context().traceId();
    MDC.put("traceId", traceId);

    try {
        log.info("[流式] 开始 sessionId={}", sessionId);
        // ...
    } finally {
        MDC.clear();  // 防止线程池复用时污染
    }
}
```

**配置：**
```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0       # 100%采样（生产环境可调为0.1）
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

**关键点：**
- `MDC`（Mapped Diagnostic Context）是 SLF4J 的上下文传递机制
- `MDC.put("traceId", ...)` 后，所有 `log.info()` 自动包含 traceId
- 必须在 `finally` 中 `MDC.clear()`，否则线程池复用时 traceId 会串
- OTLP 是 OpenTelemetry 的标准协议，兼容 Jaeger / Zipkin / Grafana Tempo

---

## 10. 路径遍历漏洞修复

### 问题场景

```java
// ❌ 原代码
@GetMapping("/tts/{filename}")
public ResponseEntity<Resource> ttsAudio(@PathVariable String filename) {
    Path audioPath = Paths.get(System.getProperty("java.io.tmpdir"), "tts", filename);
    // filename = "../../etc/passwd" → 可访问任意文件！
    if (!Files.exists(audioPath)) {
        return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(new FileSystemResource(audioPath));
}
```

### 修复

```java
// ✅ 修复后
@GetMapping("/tts/{filename}")
public ResponseEntity<Resource> ttsAudio(@PathVariable String filename) {

    // 1. 白名单校验：只允许 .wav 文件
    if (!filename.toLowerCase().endsWith(".wav")) {
        log.warn("[TTS] 非法文件类型: {}", filename);
        return ResponseEntity.badRequest().build();
    }

    // 2. 禁止路径分隔符
    if (filename.contains("/") || filename.contains("\\")) {
        log.warn("[TTS] 路径遍历攻击: {}", filename);
        return ResponseEntity.badRequest().build();
    }

    // 3. normalize + 检查必须在允许目录内
    Path audioDir = ttsService.getAudioDir();  // 配置化路径
    Path audioPath = audioDir.resolve(filename).normalize();
    if (!audioPath.startsWith(audioDir)) {
        log.warn("[TTS] 路径逃逸: {} → {}", filename, audioPath);
        return ResponseEntity.badRequest().build();
    }

    if (!Files.exists(audioPath)) {
        return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok(new FileSystemResource(audioPath));
}
```

**关键点：**
- 第一层防御：文件后缀白名单
- 第二层防御：禁止路径分隔符
- 第三层防御：`normalize()` 后检查 `startsWith(允许目录)`
- 三层防御逐级拦截，不依赖单一校验
- `normalize()` 会将 `../../etc/passwd` 解析为 `/etc/passwd`，此时 `startsWith(audioDir)` 失败

---

## 11. RabbitMQ 异步对话模式

### 问题场景

Controller 层收到游客对话请求后，需要执行 Spring AI Graph（2-5 秒）。如果在 Tomcat 线程中直接执行，高并发时 Tomcat 线程池会被全部占用，新请求无法处理。

### 改造思路

将 Graph 执行从 Tomcat 线程移交到 RabbitMQ Consumer 线程。Controller 收到请求后，将参数封装为消息投递到 RabbitMQ 队列，立即返回。Consumer 在后台异步执行 Graph，结果通过 Redis 推送给 SSE 订阅者。

```
Tomcat 线程                      RabbitMQ Consumer 线程
     │                                    │
     ├─ 接收请求                           │
     ├─ 封装 ChatRequestMessage            │
     ├─ 投递到 tourist.chat.queue ────────→├─ 执行 Graph (2-5s)
     ├─ 返回 conversationId (50ms)         ├─ 发布结果到 Redis
     │                                    ├─ 推送 Pub/Sub 事件
     ▼                                    ▼
```

### Step 1：消息 DTO（Java 17 Record）

```java
// jingbanyou-tourist/src/main/java/.../dto/ChatRequestMessage.java

public record ChatRequestMessage(
        /**
         * 唯一请求 ID，用于关联 SSE 结果
         */
        String conversationId,
        /**
         * 用户消息
         */
        String message,
        /**
         * 对话上下文 ID（用于 ChatMemory）
         */
        String sessionId,
        /**
         * 景区 ID
         */
        Long scenicId,
        /**
         * 访客 ID
         */
        String visitorId
) implements Serializable {
}
```

**关键点：**
- Record 自动生成构造器、getter、equals/hashCode/toString
- `implements Serializable` 是 RabbitMQ 序列化传输的前提
- 5 个字段覆盖 Graph 执行所需的最小参数集

### Step 2：队列拓扑配置

```java
// jingbanyou-tourist/src/main/java/.../config/RabbitMQConfig.java

@Configuration
public class RabbitMQConfig {

    public static final String TOURIST_EXCHANGE = "jingbanyou.tourist.exchange";
    public static final String TOURIST_DLX = "jingbanyou.tourist.dlx";
    public static final String CHAT_QUEUE = "tourist.chat.queue";
    public static final String CHAT_DLQ = "tourist.chat.dlq";
    public static final String CHAT_ROUTING_KEY = "tourist.chat";

    @Bean
    public TopicExchange touristExchange() {
        return new TopicExchange(TOURIST_EXCHANGE);
    }

    @Bean
    public Queue chatQueue() {
        return QueueBuilder.durable(CHAT_QUEUE)
                .deadLetterExchange(TOURIST_DLX)       // 声明死信交换机
                .deadLetterRoutingKey(CHAT_ROUTING_KEY + ".dlq")
                .build();
    }

    @Bean
    public Binding chatBinding() {
        return BindingBuilder
                .bind(chatQueue())
                .to(touristExchange())
                .with(CHAT_ROUTING_KEY);
    }

    // ============ 死信交换机 + 队列（TTL 60s 后自动重试一次） ============
    @Bean
    public Queue chatDlq() {
        return QueueBuilder.durable(CHAT_DLQ)
                .ttl(60000)                             // 死信队列 TTL 60s
                .deadLetterExchange(TOURIST_EXCHANGE)   // 过期后投递回主交换机
                .deadLetterRoutingKey(CHAT_ROUTING_KEY)
                .build();
    }

    // ============ JSON 序列化 ============
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                        Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
```

**关键点：**
- `QueueBuilder.durable()` 持久化队列，RabbitMQ 重启后不丢消息
- 死信队列 TTL 60s：消费失败后 60 秒自动回主队列重试，避免死循环
- `Jackson2JsonMessageConverter` 替代默认的 `SimpleMessageConverter`，自动序列化 Record

### Step 3：Consumer 异步消费

```java
// jingbanyou-tourist/src/main/java/.../consumer/ChatRequestConsumer.java

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRequestConsumer {

    private final ITouristFacadeService facadeService;
    private final IStreamingAnswerService streamingAnswerService;
    private final SseResultPublisher resultPublisher;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.CHAT_QUEUE)
    public void handleChatRequest(ChatRequestMessage msg) {
        String conversationId = msg.conversationId();
        log.info("[Consumer] 收到请求 conversationId={}, message={}", conversationId, msg.message());

        try {
            // 1. 校验/创建访客会话
            facadeService.ensureTouristSession(msg.visitorId(), msg.scenicId());

            // 2. 执行 Graph（阻塞 2-5 秒，在 Consumer 线程而非 Tomcat 线程）
            long graphStart = System.currentTimeMillis();
            OverAllState result = facadeService.executeGraph(
                    msg.message(), msg.sessionId(), msg.scenicId(), msg.visitorId()
            );
            long graphCost = System.currentTimeMillis() - graphStart;

            // 3. 保存对话元数据 + 发布 metadata 事件
            facadeService.saveChatMetadata(msg.sessionId(), intent, tokensUsed, modelUsed, ...);
            resultPublisher.publishMetadata(conversationId, metadataData);

            // 4. 订阅流式回答，每个事件写入 Redis
            streamingAnswerService.streamAnswer(ctx)
                    .doOnNext(sse -> {
                        String eventName = sse.event() != null ? sse.event() : "";
                        String data = sse.data() != null ? sse.data() : "";
                        String id = sse.id() != null ? sse.id() : String.valueOf(System.currentTimeMillis());
                        resultPublisher.publish(conversationId, eventName, data, id);
                    })
                    .doOnComplete(() -> resultPublisher.publishDone(conversationId))
                    .doOnError(e -> {
                        log.error("[Consumer] 流式回答失败 conversationId={}", conversationId, e);
                        resultPublisher.publishError(conversationId, e.getMessage());
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("[Consumer] 处理失败 conversationId={}", conversationId, e);
            resultPublisher.publishError(conversationId, e.getMessage());
        }
    }
}
```

**关键点：**
- `@RabbitListener(queues = RabbitMQConfig.CHAT_QUEUE)` 绑定队列，自动反序列化为 `ChatRequestMessage`
- Graph 执行使用 Consumer 线程池，Tomcat 线程已释放
- `streamAnswer(ctx)` 返回 `Flux`，通过 `.doOnNext()` 逐事件写入 Redis
- `.subscribe()` 触发惰性 Flux 实际执行
- 异常统一捕获并通过 `publishError()` 通知前端

---

## 12. SSE 流桥接（Redis Pub/Sub + List）

### 问题场景

Consumer 在独立线程执行 Graph，SSE 端点（Controller）运行在 Tomcat 线程。两者如何通信？直接线程间传递不可行，需要中间媒介。

### 改造思路

采用双通道 Redis 设计：

```
Consumer 线程                          SSE 端点 (Tomcat 线程)
     │                                        │
     ├─ rightPush(events:xxx, json) ─────────→├─ range(events:xxx, ..) 读取已有事件
     │                                        │
     ├─ convertAndSend(sse:xxx, json) ───────→├─ RedisMessageListenerContainer 实时接收
     │                                        │
     └─ publishDone / publishError ──────────→├─ 结束 Flux
```

- **Redis List**（`tourist:sse:events:{conversationId}`）：持久化所有事件，支持 late-joiner（SSE 重连后可以回放已有事件）
- **Redis Pub/Sub**（`tourist:sse:{conversationId}`）：实时推送新事件，低延迟
- **Status Key**（`tourist:sse:status:{conversationId}`）：标记对话是否已完成
- 所有 Key 5 分钟 TTL，自动清理

### Step 1：SSE 事件 DTO

```java
// jingbanyou-tourist/src/main/java/.../dto/SseMessage.java

public record SseMessage(
        /**
         * 事件类型：metadata, answer_fragment, audio, answer, done, error
         */
        String event,
        /**
         * 事件数据（JSON 字符串，与 SseEventFactory 格式一致）
         */
        String data,
        /**
         * 事件 ID（用于去重）
         */
        String id,
        /**
         * 时间戳
         */
        long timestamp
) implements Serializable {
}
```

### Step 2：生产者 — SseResultPublisher

```java
// jingbanyou-tourist/src/main/java/.../service/sse/SseResultPublisher.java

@Slf4j
@Service
@RequiredArgsConstructor
public class SseResultPublisher {

    private static final String EVENTS_KEY_PREFIX = "tourist:sse:events:";
    private static final String STATUS_KEY_PREFIX = "tourist:sse:status:";
    private static final String CHANNEL_PREFIX = "tourist:sse:";
    private static final long TTL_SECONDS = 300;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 发布 SSE 事件到 Redis
     */
    public void publish(String conversationId, String event, String data, String id) {
        try {
            SseMessage msg = new SseMessage(event, data, id, System.currentTimeMillis());
            String json = objectMapper.writeValueAsString(msg);

            // 1. 写入 List（持久化，支持 late-joiner）
            stringRedisTemplate.opsForList().rightPush(EVENTS_KEY_PREFIX + conversationId, json);
            stringRedisTemplate.expire(EVENTS_KEY_PREFIX + conversationId, TTL_SECONDS, TimeUnit.SECONDS);

            // 2. 推送 Pub/Sub（实时通知订阅者）
            stringRedisTemplate.convertAndSend(CHANNEL_PREFIX + conversationId, json);

        } catch (Exception e) {
            log.error("[SSE发布] 失败 conversationId={}", conversationId, e);
        }
    }

    /**
     * 发布 done 事件
     */
    public void publishDone(String conversationId) {
        publish(conversationId, "done",
                "{\"totalCostMs\":" + System.currentTimeMillis() + "}", "done");
        setStatus(conversationId, "completed");  // 标记完成，阻止 late-joiner 无限等待
    }

    /**
     * 发布 error 事件
     */
    public void publishError(String conversationId, String errorMsg) {
        String data = "{\"content\":\"\",\"error\":\"" + errorMsg
                + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
        publish(conversationId, "error", data, "error");
        setStatus(conversationId, "completed");
    }
}
```

**关键点：**
- 每次 `publish()` 同时写 List 和发 Pub/Sub，确保两类订阅者都能收到
- 写完 List 后立即 `expire()` 续期 TTL
- `publishDone()` 和 `publishError()` 都会设置 status 为 `completed`
- 快捷方法 `publishMetadata()`, `publishAnswer()` 封装常见事件类型

### Step 3：消费者 — SseStreamBridge

```java
// jingbanyou-tourist/src/main/java/.../service/sse/SseStreamBridge.java

@Slf4j
@Service
@RequiredArgsConstructor
public class SseStreamBridge {

    private static final String EVENTS_KEY_PREFIX = "tourist:sse:events:";
    private static final String CHANNEL_PREFIX = "tourist:sse:";
    private static final String STATUS_KEY_PREFIX = "tourist:sse:status:";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    /**
     * 已完成标记（避免 done/error 之后的事件被处理）
     */
    private final Map<String, AtomicBoolean> completed = new ConcurrentHashMap<>();

    /**
     * 创建 SSE 事件流
     */
    public Flux<ServerSentEvent<String>> createSseFlux(String conversationId) {
        String listKey = EVENTS_KEY_PREFIX + conversationId;
        String channel = CHANNEL_PREFIX + conversationId;
        String statusKey = STATUS_KEY_PREFIX + conversationId;

        completed.put(conversationId, new AtomicBoolean(false));

        return Flux.<ServerSentEvent<String>>create(emitter -> {

            // 1. 先读取 List 中已有的事件（late-joiner 支持）
            List<String> existing = stringRedisTemplate.opsForList().range(listKey, 0, -1);
            if (existing != null) {
                for (String json : existing) {
                    if (emitter.isCancelled()) return;
                    ServerSentEvent<String> sse = parseSse(json);
                    if (sse != null) {
                        emitter.next(sse);
                        if ("done".equals(sse.event()) || "error".equals(sse.event())) {
                            completed.get(conversationId).set(true);
                            emitter.complete();
                            return;
                        }
                    }
                }
            }

            // 2. 检查是否已完成（消费者比订阅者先结束的情况）
            String status = stringRedisTemplate.opsForValue().get(statusKey);
            if ("completed".equals(status)) {
                emitter.complete();
                return;
            }

            // 3. 订阅 Redis Pub/Sub，实时接收新事件
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);

            MessageListener listener = (Message msg, byte[] pattern) -> {
                if (emitter.isCancelled()) return;
                AtomicBoolean done = completed.get(conversationId);
                if (done != null && done.get()) return;

                String json = new String(msg.getBody());
                ServerSentEvent<String> sse = parseSse(json);
                if (sse != null) {
                    emitter.next(sse);
                    if ("done".equals(sse.event()) || "error".equals(sse.event())) {
                        if (done != null) done.set(true);
                        emitter.complete();
                    }
                }
            };

            container.addMessageListener(listener, new ChannelTopic(channel));
            container.start();

            // 4. 断开时清理
            emitter.onDispose(container::stop);

        }).subscribeOn(Schedulers.boundedElastic());
    }
}
```

**关键点：**
- `Flux.create()` 桥接回调式 API（Redis Pub/Sub）到响应式流
- **Late-joiner 支持**：先遍历 List 回放已有事件，再订阅 Pub/Sub 接收新事件
- `completed` 标记（`ConcurrentHashMap<String, AtomicBoolean>`）防止 done/error 之后的冗余事件被发射
- `subscribeOn(Schedulers.boundedElastic())` 将阻塞的 Redis 监听操作隔离到弹性线程池
- `emitter.onDispose(container::stop)` 在客户端断开 SSE 连接时释放 Redis 订阅资源
- Status Key 兜底：如果 Consumer 在线程订阅之前已经完成，直接 `emitter.complete()`

---

## 13. 游客会话管理（visitorId + 在线人数统计）

### 问题场景

游客端无需注册登录，但后端需要识别每次对话属于哪个用户、在哪个景区、在线人数是多少。这些信息对对话上下文维护和运营统计至关重要。

### 改造思路

前端首次访问时生成 UUID 作为 `visitorId`，存储到 localStorage，后续请求通过请求头 `X-Visitor-Id` 携带。后端通过 `TouristSessionFilter` 透明拦截所有 `/tourist/**` 请求，校验/创建 Redis 会话，并记录在线心跳。

```
前端                           Filter                         Redis
 │                              │                              │
 ├─ 生成 UUID                   │                              │
 ├─ 存入 localStorage           │                              │
 ├─ 请求带 X-Visitor-Id ──────→├─ extractVisitorId()          │
 │                              ├─ getOrCreateSession() ──────→├─ 创建/续期 Hash (TTL 2h)
 │                              ├─ heartbeat() ──────────────→├─ ZADD online:{sceneId}
 │                              ├─ request.setAttribute()      │
 │                              ├─ chain.doFilter()            │
 │                              │                              │
 │  ← 业务响应                  │                              │
```

### Step 1：会话 DTO

```java
// jingbanyou-common/src/main/java/.../domain/VisitorSessionDTO.java

public class VisitorSessionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 游客唯一标识 */
    private String visitorId;
    /** 景区ID */
    private Long sceneId;
    /** 入口ID */
    private String entranceId;
    /** 首次访问时间（毫秒时间戳） */
    private Long firstVisitTime;
    /** 最后活跃时间（毫秒时间戳） */
    private Long lastActiveTime;

    public VisitorSessionDTO(String visitorId, Long sceneId, String entranceId) {
        this.visitorId = visitorId;
        this.sceneId = sceneId;
        this.entranceId = entranceId;
        long now = System.currentTimeMillis();
        this.firstVisitTime = now;
        this.lastActiveTime = now;
    }

    // getters / setters ...
}
```

### Step 2：Filter 拦截与会话管理

```java
// jingbanyou-framework/src/main/java/.../filter/TouristSessionFilter.java

@Slf4j
@Component
@RequiredArgsConstructor
public class TouristSessionFilter extends OncePerRequestFilter {

    private static final String HEADER_VISITOR_ID = "X-Visitor-Id";
    public static final String REQUEST_ATTR_SESSION = "visitorSession";

    /** visitorId 提取优先级：请求头 > URL 参数 */
    private static final String[] VISITOR_ID_KEYS = {
            "visitorId", "visitor_id", "visitorid"
    };

    private final TouristSessionService touristSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        try {
            // 1. 尝试从请求中提取 visitorId
            String visitorId = extractVisitorId(request);

            if (StringUtils.isNotEmpty(visitorId)) {
                // 2. 从 URL 参数获取 sceneId 和 entranceId
                Long sceneId = parseLong(request.getParameter("sceneId"));
                String entranceId = request.getParameter("entranceId");

                // 3. 校验或创建会话
                VisitorSessionDTO session = touristSessionService
                        .getOrCreateSession(visitorId, sceneId, entranceId);

                // 4. 记录在线心跳（按景区维度实时统计在线人数）
                touristSessionService.heartbeat(visitorId, sceneId);

                // 5. 将会话信息存入 request attribute
                request.setAttribute(REQUEST_ATTR_SESSION, session);
            }

            chain.doFilter(request, response);

        } catch (Exception e) {
            log.error("[游客会话] 过滤器异常，放行请求", e);
            chain.doFilter(request, response);  // 异常放行，不影响业务
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/tourist");
    }

    private String extractVisitorId(HttpServletRequest request) {
        // 1. 优先从请求头获取
        String visitorId = request.getHeader(HEADER_VISITOR_ID);
        if (StringUtils.isNotEmpty(visitorId)) return visitorId;

        // 2. 从 URL 参数获取
        for (String key : VISITOR_ID_KEYS) {
            visitorId = request.getParameter(key);
            if (StringUtils.isNotEmpty(visitorId)) return visitorId;
        }
        return null;
    }
}
```

**关键点：**
- 继承 `OncePerRequestFilter`，确保每次请求只执行一次
- `shouldNotFilter()` 精准限定路径 `/tourist`，不影响管理端
- 不从 POST body 读取 visitorId（避免消费 InputStream 导致 Controller `@RequestBody` 失效）
- 异常时 `chain.doFilter(request, response)` 放行，Filter 失败不影响业务
- 通过 `request.setAttribute()` 将会话透传给 Controller

### Step 3：会话服务 — 核心逻辑

```java
// jingbanyou-common/src/main/java/.../service/TouristSessionService.java

@Slf4j
@Service
@RequiredArgsConstructor
public class TouristSessionService {

    private static final long SESSION_TIMEOUT = 2L;  // 会话有效期 2 小时
    private final RedisCache redisCache;

    /**
     * 获取或创建会话
     */
    public VisitorSessionDTO getOrCreateSession(String visitorId, Long sceneId, String entranceId) {
        String key = CacheConstants.VISITOR_SESSION_KEY + visitorId;
        VisitorSessionDTO session = redisCache.getCacheObject(key);

        if (session == null) {
            // 会话不存在，创建新会话
            session = new VisitorSessionDTO(visitorId, sceneId, entranceId);
            redisCache.setCacheObject(key, session, (int) SESSION_TIMEOUT, TimeUnit.HOURS);
            log.info("[游客会话] 创建新会话: visitorId={}, sceneId={}", visitorId, sceneId);
        } else {
            // 会话存在，更新最后活跃时间并续期
            session.setLastActiveTime(System.currentTimeMillis());
            redisCache.setCacheObject(key, session, (int) SESSION_TIMEOUT, TimeUnit.HOURS);
        }

        return session;
    }

    /**
     * 记录游客在线心跳（按景区维度，每个请求刷新一次时间戳）
     */
    public void heartbeat(String visitorId, Long sceneId) {
        if (sceneId == null || visitorId == null || visitorId.isBlank()) return;
        String key = CacheConstants.VISITOR_ONLINE_KEY + sceneId;
        redisCache.zAdd(key, visitorId, System.currentTimeMillis());
        redisCache.expire(key, 24, TimeUnit.HOURS);
    }

    /**
     * 获取景区实时在线人数（过去2小时内有心跳的游客）
     */
    public long getOnlineCount(Long sceneId) {
        if (sceneId == null) return 0;
        String key = CacheConstants.VISITOR_ONLINE_KEY + sceneId;
        long twoHoursAgo = System.currentTimeMillis() - 2 * 3600 * 1000L;
        return redisCache.zCount(key, twoHoursAgo, Long.MAX_VALUE);
    }

    /**
     * 获取景区当日累计游客数
     */
    public long getDailyVisitorCount(Long sceneId) {
        if (sceneId == null) return 0;
        String key = CacheConstants.VISITOR_ONLINE_KEY + sceneId;
        return redisCache.zCard(key);
    }
}
```

### 数据结构

| Redis Key | 结构 | 用途 | TTL |
|-----------|------|------|-----|
| `visitor:session:{visitorId}` | Hash (VisitorSessionDTO) | 游客会话详情 | 2h |
| `visitor:online:{sceneId}` | ZSet (member=visitorId, score=时间戳) | 在线心跳 | 24h |

**关键点：**
- **会话 Redis 存储**：`visitor:session:{visitorId}` 存 `VisitorSessionDTO`（Hash 结构），TTL 2 小时，每次请求续期
- **在线统计使用 ZSet**：`visitor:online:{sceneId}` 是 Sorted Set，member 是 visitorId，score 是毫秒时间戳
- **实时在线人数**：`zCount(key, now-2h, +inf)` 统计过去 2 小时内有过心跳的用户数
- **当日累计游客数**：`zCard(key)` 获取 ZSet 总元素个数（24h TTL）
- **heartbeat 在 Filter 中自动执行**，业务代码无需关心

---

## 14. 高优先级安全修复（isAdmin / 异常泄露 / JWT密钥）

### 问题场景

代码审计发现 3 个高优先级安全问题：

| # | 问题 | 风险 | 严重程度 |
|---|------|------|----------|
| 1 | `SecurityUtils.isAdmin()` 硬编码 `userId == 1L` | 管理员 ID 不可变更，无法适配多管理员场景 | 高 |
| 2 | `GlobalExceptionHandler` 将 `e.getMessage()` 返回客户端 | 内部异常消息（SQL 语句、栈帧、类名）泄露给攻击者 | 高 |
| 3 | JWT 密钥有默认回退值 `jingbanyou-admin-default-jwt-secret-2024-key` | 若忘记配置环境变量，使用弱密钥签发令牌，可被伪造 | 高 |

---

### Fix 1：isAdmin 从硬编码改为可配置

#### 改造思路

将 `superAdminId` 从硬编码常量改为 Spring `@Value` 注入，支持 YAML 覆盖，同时保持默认行为不变（`1L`）。

```
修改前：
  isAdmin(userId) → userId == 1L ? true : false

修改后：
  isAdmin(userId) → userId == superAdminId ? true : false
                         ↑
                   @Value("${sys.user.super-admin-id:...}")
                   (默认 1L，可 YAML 覆盖)
```

#### 代码实现

```java
// jingbanyou-common/.../utils/SecurityUtils.java

public class SecurityUtils
{
    /** 超级管理员ID配置键 */
    public static final String SUPER_ADMIN_ID_KEY = "sys.user.superAdminId";

    /** 默认超级管理员ID */
    public static final long DEFAULT_SUPER_ADMIN_ID = 1L;

    /** 当前生效的超级管理员ID */
    private static long superAdminId = DEFAULT_SUPER_ADMIN_ID;

    /**
     * Spring 注入配置值。
     * 使用 SpEL T() 表达式引用常量作为回退值，
     * 避免在注解中硬编码数字。
     */
    @Value("${sys.user.super-admin-id:#{T(cn.edu.gdou.jingbanyou.common.utils.SecurityUtils).DEFAULT_SUPER_ADMIN_ID}}")
    public void setSuperAdminId(long id) {
        SecurityUtils.superAdminId = id;
    }

    // 修改前：return userId != null && 1L == userId;
    // 修改后：
    public static boolean isAdmin(Long userId)
    {
        return userId != null && superAdminId == userId;
    }
}
```

**关键点：**
- 常量 `DEFAULT_SUPER_ADMIN_ID` 和静态字段 `superAdminId` 分离：常量仅用于 SpEL 回退引用，静态字段存运行时值
- SpEL `T(FullClassName).CONSTANT` 语法让 Spring 解析时读取类常量，避免在注解中写死数字
- `setSuperAdminId()` 是实例方法（Spring 需要代理），内部赋值给静态字段（兼容所有静态调用方）
- **零调用方改动**：12 个调用点（`DataScopeAspect`, `SysUserServiceImpl`, `SysRoleServiceImpl`, `SysMenuServiceImpl` 等）无需任何修改

#### 配置方式

```yaml
# application.yml（可选配置，不配则默认 1L）
sys:
  user:
    super-admin-id: 1
```

---

### Fix 2：GlobalExceptionHandler 隐藏异常消息

#### 改造思路

`RuntimeException` 和 `Exception` 是两个兜底处理器。原代码将 `e.getMessage()` 直接返回给客户端，可能泄露内部实现细节。改为返回通用消息，完整堆栈仍记录在服务端日志中。

```
修改前：
  RuntimeException → AjaxResult.error(e.getMessage())
  Exception        → AjaxResult.error(e.getMessage())

修改后：
  RuntimeException → AjaxResult.error(HttpStatus.ERROR, "系统内部异常，请联系管理员")
  Exception        → AjaxResult.error(HttpStatus.ERROR, "系统内部异常，请联系管理员")
```

#### 代码实现

```java
// jingbanyou-framework/.../web/exception/GlobalExceptionHandler.java

/**
 * 拦截未知的运行时异常
 */
@ExceptionHandler(RuntimeException.class)
public AjaxResult handleRuntimeException(RuntimeException e, HttpServletRequest request)
{
    String requestURI = request.getRequestURI();
    log.error("请求地址'{}',发生未知异常.", requestURI, e);  // ← 完整堆栈仍记录在服务端
    return AjaxResult.error(HttpStatus.ERROR, "系统内部异常，请联系管理员");  // ← 只返回通用消息
}

/**
 * 系统异常
 */
@ExceptionHandler(Exception.class)
public AjaxResult handleException(Exception e, HttpServletRequest request)
{
    String requestURI = request.getRequestURI();
    log.error("请求地址'{}',发生系统异常.", requestURI, e);
    return AjaxResult.error(HttpStatus.ERROR, "系统内部异常，请联系管理员");
}
```

**关键点：**
- `log.error(..., e)` 第三个参数传入异常对象，会将完整堆栈写入日志文件
- 客户端只收到通用消息 `"系统内部异常，请联系管理员"`，无法获取任何内部信息
- `ServiceException` 处理器保持不变——业务异常需要向客户端透传消息（如"密码错误"）
- `AjaxResult.error(int code, String msg)` 使用 `HttpStatus.ERROR`（500）作为状态码

---

### Fix 3：JWT 密钥强制环境变量

#### 改造思路

原配置 `secret: ${JWT_SECRET:jingbanyou-admin-default-jwt-secret-2024-key}` 存在两个问题：

1. **默认值本身是弱密钥**：`jingbanyou-admin-default-jwt-secret-2024-key` 长度足够但已被源码泄露，不应再使用
2. **`RuoYiApplication.run()` 中的启动检查形同虚设**：`CommandLineRunner` 在 Spring 启动后执行，此时 `@Value("${token.secret}")` 已经用默认值完成了属性注入，检查代码读到的是已注入的属性值而非原始环境变量

修复方案：
- 移除 YAML 中的默认值，让 Spring 在属性解析阶段就因缺少 `JWT_SECRET` 而失败
- 删除无效的 `CommandLineRunner` 检查
- 在 `TokenService` 中添加 `@PostConstruct` 密钥长度校验作为双重保险

```
修改前：
  application.yml:  secret: ${JWT_SECRET:jingbanyou-admin-default-jwt-secret-2024-key}
  RuoYiApplication: CommandLineRunner.run() → 检查 System.getenv()（无效）
  启动行为:          未设 JWT_SECRET → 使用默认弱密钥 → 启动成功

修改后：
  application.yml:  secret: ${JWT_SECRET}
  RuoYiApplication: 移除 CommandLineRunner
  TokenService:     @PostConstruct validateSecret() → 检查长度 ≥ 32
  启动行为:          未设 JWT_SECRET → Spring 启动失败（属性解析阶段即报错）
```

#### 代码实现

**application.yml：**
```yaml
# jingbanyou-admin/src/main/resources/application.yml
token:
  header: Authorization
  secret: ${JWT_SECRET}  # 移除默认值，未配置则 Spring 启动失败
  expireTime: 30
```

**RuoYiApplication.java（精简后）：**
```java
// jingbanyou-admin/.../RuoYiApplication.java

@Slf4j
@EnableFileStorage
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class, MybatisAutoConfiguration.class })
@EnableAsync
public class RuoYiApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(RuoYiApplication.class, args);
        log.info("若依系统启动成功");
    }
}
```

**TokenService.java（新增校验）：**
```java
// jingbanyou-framework/.../web/service/TokenService.java

@Component
public class TokenService
{
    @Value("${token.secret}")
    private String secret;

    /**
     * 校验 JWT 密钥长度，确保安全性。
     * 在 Bean 初始化阶段执行，比 CommandLineRunner 更早发现配置问题。
     */
    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "JWT secret must be at least 32 characters. Current length: " +
                (secret == null ? 0 : secret.length()));
        }
    }

    // ... 其余方法不变
}
```

**关键点：**
- Spring 解析 `${JWT_SECRET}` 时，若环境变量不存在且无默认值，则直接启动失败（`IllegalArgumentException: Could not resolve placeholder 'JWT_SECRET'`）
- `@PostConstruct` 校验在依赖注入完成后、Bean 可用前执行，提供长度兜底检查
- 生成强密钥命令：`openssl rand -base64 48`

---

## 总结

| 模式 | 适用场景 | 核心工具/技术 |
|------|---------|--------------|
| 注解 + Filter | 轻量级认证/鉴权 | `@CustomAnnotation`, `OncePerRequestFilter` |
| CircuitBreaker | 外部API保护 | Resilience4j + `@CircuitBreaker` |
| CompletableFuture | 同步阻塞 → 异步 | `CompletableFuture.supplyAsync()` |
| 多控制器拆分 | 控制器瘦身 | 按领域拆分为 4 个聚焦 Controller |
| 方法拆分 | 长方法重构 | 单一职责原则，每个方法 < 30行 |
| 错误码枚举 | 统一异常处理 | `enum ErrorCode` + `BaseController.error()` |
| Record DTO | 多参数封装 | Java 17 `record` |
| 接口分离 | 模块解耦 | 接口放 common，实现放具体模块 |
| 常量类 | 消除魔法字符串 | `final class` + `static final String` |
| MDC + OpenTelemetry | 链路追踪 | `Tracer` + `MDC.put("traceId")` |
| 路径白名单 | 路径遍历防御 | `.endsWith()`, `.contains()`, `.normalize()`, `.startsWith()` |
| RabbitMQ 异步对话 | Graph 执行不阻塞 Tomcat | `RabbitTemplate`, `@RabbitListener`, DLX 重试 |
| Redis Pub/Sub + List | Consumer-SSE 线程间桥接 | `Flux.create()`, `RedisMessageListenerContainer`, late-joiner |
| Filter + ZSet | 无登录会话 + 在线统计 | `OncePerRequestFilter`, `zAdd`, `zCount` |
| @Value + SpEL + @PostConstruct | 安全加固：去硬编码/防泄露/强制配置 | `@Value` SpEL回退, `log.error(Throwable)`, `@PostConstruct` |
