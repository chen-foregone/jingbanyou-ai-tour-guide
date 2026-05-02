# AI 导游流式对话技术文档

## 1. 整体架构

```
用户 → TouristController (/api/tourist/stream)
         ↓
    Spring AI Graph（状态机 + 意图识别）
         ↓
    HybridRetrievalNode / GeneralChatFallbackNode
    （AI 生成 + Token 级流式输出）
         ↓
    StreamingAnswerService
    （段落缓冲 + SSE 事件推送）
         ↓
    TtsService（CosyVoice 语音合成，流式返回）
         ↓
    前端（SSE 接收，音频片段实时播放）
```

---

## 2. 核心技术：Server-Sent Events（SSE）

### 2.1 什么是 SSE

SSE 是一种服务端推送技术，基于 HTTP 协议，允许服务端**单方向**向浏览器推送事件。前端通过 `EventSource` 接收。

```
HTTP/1.1 200 OK
Content-Type: text/event-stream

event: metadata
data: {"intent":"spot_question","graphCostMs":120,"timestamp":1746000000000}

event: answer_fragment
data: {"content":"动物园每天8:00开门"}

event: audio
data: {"seq":1,"chunk":"base64...","audioCostMs":50,"serverTime":1746000000100}

event: done
data: {"content":"","totalCostMs":320}
```

### 2.2 为什么用 SSE 而不是 WebSocket

| 特性 | SSE | WebSocket |
|------|-----|-----------|
| 方向 | 单向（服务端→客户端） | 双向 |
| 协议 | HTTP/1.1 | 独立协议 |
| 重连 | 自动重连 | 需手动处理 |
| 复杂度 | 简单 | 复杂 |
| 适用场景 | **推送为主**（本项目场景） | 双向交互（聊天IM等） |

本项目是对话 AI 回复，只有 AI → 用户单向推送，SSE 更轻量合适。

### 2.3 Spring WebFlux 中的 SSE

使用 `Flux<ServerSentEvent<T>>` 作为返回类型：

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chatStream(@RequestBody Map<String, Object> request) {
    return Flux.just(metadataSse(...))              // 元数据事件
               .concatWith(streamingAnswerService.streamAnswer(...))  // 流式答案 + 音频
               .concatWith(Flux.just(doneSse(...))); // 完成事件
}
```

---

## 3. 多层流式处理

### 3.1 Token 级流（AI 模型输出）

AI 模型（如通义千问）输出是 **token 流**，不是一次性返回完整文本。

```
模型输出: "动物园" → "每天" → "8:" → "00" → "开门" → "。"
```

Spring AI 的 `ChatClient.call().stream()` 将 token 流封装为 `Flux<String>`，每个 String 是一个片段。

### 3.2 段落级缓冲（Paragraph Buffering）

**问题**：token 级推送太频繁，且 TTS 合成需要完整句子。

**解决**：StreamingAnswerService 维护一个 StringBuilder 缓冲区，积累到以下条件时 flush：
- 遇到段落结束符：`。！？．`
- 缓冲区超过 200 字符

```java
// 检测段落边界
private boolean shouldFlush(StringBuilder buffer) {
    if (buffer.length() >= MAX_PARAGRAPH_CHARS) return true;
    char last = buffer.charAt(buffer.length() - 1);
    return PARAGRAPH_END_CHARS.contains(last);
}
```

### 3.3 段落级 SSE 推送

每个段落 flush 后，立即发送两个 SSE 事件：

```java
// 1. 文本片段事件
event: answer_fragment
data: {"content":"动物园每天8:00开门。"}

// 2. 音频片段事件
event: audio
data: {"seq":1,"chunk":"base64音频数据...","audioCostMs":50,"serverTime":1746000000100}
```

---

## 4. TTS 语音合成流

### 4.1 流式 TTS 流程

```
段落文本 → CosyVoice API → PCM 音频流
                              ↓
                       分割为 chunk（4096 bytes）
                              ↓
                       Base64 编码
                              ↓
                       SSE audio 事件推送
```

### 4.2 CosyVoice 流式合成

CosyVoice SDK 返回 `Flux<byte[]>`（PCM chunks），每个 chunk 是原始音频字节。

```java
// TtsService 实现
public Flux<byte[]> streamAudio(String text, DigitalHumanConfig digitalHuman) {
    CosyVoiceClient client = CosyVoiceClientBuilder.builder()
            .model(digitalHuman.getTtsVoiceCode())
            .build();
    return client.synthesizeStream(text);
}
```

### 4.3 段落级触发（与文本同步）

每个段落 flush 后，**同步触发 TTS**：

```java
// 一个段落的处理
return Flux.concat(
    Mono.just(answerFragmentSse(paragraph)),  // 先发文本
    ttsService.streamAudio(ttsText, digitalHuman)  // 再发音频流
        .map(chunk -> audioSse(chunk, seq++, startTimestamp))
);
```

这保证了：**先显示文字，后播放声音**，顺序一致。

---

## 5. Spring AI Graph（状态机）

### 5.1 Graph 是什么

Spring AI Graph 是一个**有向图**，节点是处理单元（Node），边是状态转换规则。

```
用户消息
    ↓
[入口] → 意图识别（TextDistinguishNode）
              ↓
    ┌─────────┼──────────┐
    ↓         ↓          ↓
景点问答   路线规划    闲聊兜底
(Hybrid    (Route      (GeneralChat
Retrieval) Polish)    Fallback)
```

### 5.2 核心节点

| 节点 | 作用 |
|------|------|
| `TextDistinguishNode` | 意图识别，决定走哪条分支 |
| `HybridRetrievalNode` | RAG 检索 + 生成，回答景点问题 |
| `RoutePolishNode` | 调用地图 API 生成路线 |
| `GeneralChatFallbackNode` | 通用闲聊（无知识库时） |
| `ProfileUpdaterNode` | 更新游客画像（偏好收集） |

### 5.3 状态传递

节点之间通过 `OverAllState`（Map）传递数据：

```java
// 设置状态
Map<String, Object> state = new HashMap<>();
state.put(GraphStateKey.QUESTION, message);
state.put(GraphStateKey.SCENIC_ID, scenicId);
state.put(GraphStateKey.LANGUAGE, "zh");

// Graph 执行
CompiledGraph graph = streamGraphConfiguration.streamCompiledGraph();
OverAllState result = graph.invoke(state);

// 读取结果
String answer = (String) result.value(GraphStateKey.ANSWER).orElse("");
String intent = (String) result.value(GraphStateKey.INTENT).orElse("");
```

---

## 6. Redis 会话管理

### 6.1 为什么要用 Redis

AI 生成需要**多轮上下文**，但 HTTP 是无状态的。Redis 存储会话历史，解决：
- AI 需要记住前几轮对话内容
- 多轮对话的上下文窗口管理

### 6.2 JedisRedisChatMemoryRepository

`spring-ai-alibaba-starter-memory-redis` 提供的实现，连接 Redis Stack（6380）。

```
Key: chat:memory:{sessionId}
Value: [UserMessage, AssistantMessage, UserMessage, AssistantMessage, ...]
TTL: 由 spring.ai.memory.redis.time-to-live 配置
```

```java
// 读取历史
List<Message> history = chatMemoryRepository.findByConversationId(sessionId);

// 写入
chatMemoryRepository.saveAll(sessionId, messages);
```

### 6.3 ChatMemoryAdvisor

Spring AI 的 `MessageChatMemoryAdvisor` 拦截 AI 调用，自动注入历史消息：

```java
ChatClient.builder()
    .defaultAdvisors(chatMemoryAdvisor)  // 自动管理上下文
    .build();
```

调用时通过 context 参数指定会话 ID：

```java
client.prompt()
    .advisors(ctx -> ctx.param(ChatMemory.CONVERSATION_ID, sessionId))
    .user(message)
    .call();
```

---

## 7. 对话存储（MySQL）

### 7.1 两层存储

| 层级 | 存储内容 | 生命周期 |
|------|---------|---------|
| **Redis** | 实时会话上下文（AI 需要读取） | 对话进行中 |
| **MySQL** | 持久化记录（visitor_interaction + visitor_conversation） | 永久 |

### 7.2 visitor_interaction（单轮 Q&A）

每轮对话生成一条记录：

| 字段 | 说明 |
|------|------|
| session_id | 会话ID（雪花算法生成） |
| user_question | 用户问题 |
| ai_answer | AI 回答 |
| intent_type | 意图类型 |
| turn_index | 轮次序号（从0开始） |
| emotion_detected | 情感检测结果 |
| tokens_used | 消耗 Token 数 |
| response_time_ms | 响应耗时 |

### 7.3 visitor_conversation（会话元数据）

每个会话生成一条记录：

| 字段 | 说明 |
|------|------|
| session_id | 主键，雪花ID |
| title | 会话标题（首条消息截取） |
| turn_count | 对话轮次 |
| emotion_detected | 整体情感 |
| duration_ms | 会话时长 |
| start_time / end_time | 时间范围 |

---

## 8. 情感检测（异步）

### 8.1 设计思路

情感检测是**附加功能**，不应阻塞主对话流程。采用异步 + 降级策略：

```
Graph 执行 → 触发异步情感检测（fire-and-forget）
              ↓
         有上下文 → AI 模型分析（携带历史）
              ↓
         无上下文 → 规则匹配（关键词）
              ↓
         结果暂存 Redis（chat:emotion:{sessionId}）
              ↓
         会话结束时 endChat → 读取 Redis → 写入 MySQL
```

### 8.2 为什么需要上下文

单独分析一句话容易误判：

| 场景 | 无上下文 | 有上下文 |
|------|---------|---------|
| "好的" | neutral | 前面是抱怨 → positive（接受安抚） |
| "然后呢" | neutral | 连续问5次 → 可能 negative（不耐烦） |
| "不" | negative | 前是"要退款吗" → positive（接受退款） |

---

## 9. SSE 事件类型

| 事件名 | 内容 | 用途 |
|--------|------|------|
| `metadata` | intent、graphCostMs、timestamp | 元数据 |
| `answer` | 完整文本（非流式时） | 旧兼容 |
| `answer_fragment` | 段落文本（流式时） | 前端显示文本 |
| `audio` | base64 PCM chunk、seq、audioCostMs | 前端播放音频 |
| `done` | totalCostMs | 对话完成 |
| `error` | 错误信息 | 异常处理 |

---

## 10. 雪花算法 SessionId

### 10.1 为什么用雪花ID

- **趋势有序**：可以反推会话开始时间
- **全局唯一**：分布式部署无冲突
- **纯数字**：比 UUID 更紧凑，便于存储和对比

### 10.2 雪花ID 结构

```
+---+----------------------+--------+----+
| 1 |    41位时间戳        | 10位机器 | 12位序列号 |
+---+----------------------+--------+----+
```

从雪花ID 反推时间戳：
```java
long timestamp = (id & 0x1FFFFFFFFFFFFFFFL) >> 22;
```

---

## 11. 技术栈汇总

| 层级 | 技术 |
|------|------|
| 通信协议 | SSE（Server-Sent Events） |
| 响应式框架 | Spring WebFlux（Flux / Mono） |
| AI 模型 | 阿里通义千问（DashScope） |
| AI 编排 | Spring AI Alibaba Graph |
| 会话存储 | Redis Stack（6380） |
| 持久化 | MySQL + MyBatis-Plus |
| 语音合成 | CosyVoice（TTS） |
| 语音识别 | ASR（语音→文字） |
| 情感检测 | AI 模型（异步）+ 规则匹配（降级） |
| ID 生成 | Hutool 雪花算法 |
