# 前台游客端接口改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 TouristController 统一前台入口（bootstrap/chat/tts），改造 Graph 返回结构增加 attachments，废弃旧 ChatController 接口。

**Architecture:** 在 jingbanyou-tourist 模块新建 TouristController 作为前台统一入口 `/api/tourist`。Graph 新增 AnswerPolishNode 在生成回答时同时输出结构化附件（路线卡片/景点卡片）。TTS 通过 DashScope API 合成音频返回 audioUrl。

**Tech Stack:** Spring AI Alibaba (DashScope), LangGraph (StateGraph), MyBatis-Plus, Redis

---

## 文件结构

```
jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/
├── controller/
│   ├── TouristController.java          # 新增：统一前台入口
│   └── ChatController.java             # 修改：标记旧接口废弃
├── dto/
│   ├── ChatRequest.java                # 新增：统一对话请求 DTO
│   ├── ChatResponse.java               # 新增：统一对话响应 DTO
│   ├── BootstrapVO.java                # 新增：Bootstrap 响应 VO
│   └── attachment/
│       ├── MessageAttachment.java       # 新增：附件接口
│       ├── RouteAttachment.java         # 新增：路线卡片
│       └── SpotAttachment.java         # 新增：景点卡片
├── service/
│   ├── TtsService.java                 # 新增：TTS 服务接口
│   └── impl/
│       └── DashScopeTtsServiceImpl.java # 新增：DashScope TTS 实现
├── graph/
│   ├── node/
│   │   ├── AnswerPolishNode.java      # 新增：AI 生成回答+附件节点
│   │   └── SpotRetrievalNode.java      # 新增：景点数据库检索节点
│   └── GraphConfiguration.java         # 修改：注册新节点+调整路由
└── constant/
    └── GraphStateKey.java              # 修改：新增 ATTACHMENTS/VOICE_URL 状态键
```

---

## 依赖关系

```
TouristController
  ├── TtsService (DashScopeTtsServiceImpl)
  ├── GraphConfiguration (CompiledGraph)
  ├── IScenicAreaService (manage 模块)
  ├── IDigitalHumanConfigService (manage 模块)
  └── ChatResponseService
        └── GraphStateKey / attachment DTOs
```

---

## Phase 1: 基础结构（DTO + StateKey）

### Task 1: ChatRequest / ChatResponse DTO

**Files:**
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/dto/ChatRequest.java`
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/dto/ChatResponse.java`

- [ ] **Step 1: 创建 ChatRequest.java**

```java
package cn.edu.gdou.jingbanyou.tourist.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatRequest {
    private Long scenicId;
    private String sessionId;
    private String message;       // 文本输入
    private byte[] audioData;     // 音频输入（multipart 传入时由 Controller 读取）
    private String language;       // 语言，默认 zh
}
```

- [ ] **Step 2: 创建 ChatResponse.java**

```java
package cn.edu.gdou.jingbanyou.tourist.dto;

import cn.edu.gdou.jingbanyou.tourist.dto.attachment.MessageAttachment;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ChatResponse {
    private String id;            // 消息 ID
    private String role;          // "assistant"
    private String content;       // AI 回复文本
    private List<MessageAttachment> attachments; // 结构化附件（路线卡片/景点卡片）
    private VoiceData voice;       // 语音数据
}
```

---

### Task 2: 附件 DTO（RouteAttachment / SpotAttachment）

**Files:**
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/dto/attachment/MessageAttachment.java`
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/dto/attachment/RouteAttachment.java`
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/dto/attachment/SpotAttachment.java`
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/dto/VoiceData.java`

- [ ] **Step 1: MessageAttachment.java（接口）**

```java
package cn.edu.gdou.jingbanyou.tourist.dto.attachment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = RouteAttachment.class, name = "routes"),
    @JsonSubTypes.Type(value = SpotAttachment.class, name = "spot")
})
public interface MessageAttachment {
    String getId();
    String getType();
}
```

- [ ] **Step 2: RouteAttachment.java**

```java
package cn.edu.gdou.jingbanyou.tourist.dto.attachment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteAttachment implements MessageAttachment {
    private String id;
    private String type = "routes";
    private String eyebrow;      // "路线推荐"
    private String title;
    private String meta;         // "预计 110 分钟"
    private List<RouteItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteItem {
        private String id;
        private String title;
        private String summary;
        private String duration;
        private List<String> tags;
    }
}
```

- [ ] **Step 3: SpotAttachment.java**

```java
package cn.edu.gdou.jingbanyou.tourist.dto.attachment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpotAttachment implements MessageAttachment {
    private String id;
    private String type = "spot";
    private String eyebrow;      // "景点介绍"
    private String title;
    private String description;
    private List<SpotMetric> metrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpotMetric {
        private String label;
        private String value;
    }
}
```

- [ ] **Step 4: VoiceData.java**

```java
package cn.edu.gdou.jingbanyou.tourist.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceData {
    private String audioUrl;    // TTS 合成的音频 URL
    private Integer durationMs;  // 音频时长（毫秒）
}
```

---

### Task 3: BootstrapVO

**Files:**
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/dto/BootstrapVO.java`

- [ ] **Step 1: BootstrapVO.java**

```java
package cn.edu.gdou.jingbanyou.tourist.dto;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.manage.entity.ScenicArea;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BootstrapVO {
    private ScenicAreaVO scenic;
    private DigitalHumanConfig digitalHuman;
    private List<ConversationMessage> conversation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationMessage {
        private String id;
        private String role;     // "assistant"
        private String content;  // 欢迎语
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenicAreaVO {
        private Long scenicId;
        private String scenicName;
        private String scenicDesc;
        private String coverImage;
        private List<String> topFeatures;
        private List<String> quickPrompts;
    }
}
```

---

### Task 4: GraphStateKey 新增状态键

**Files:**
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/constant/GraphStateKey.java`

- [ ] **Step 1: 在 ANSWER 定义之后添加新状态键**

在 `ANSWER = "answer"` 后添加：

```java
// ==================== 响应结构相关 ====================

/**
 * 结构化附件（路线卡片/景点卡片）
 * 类型：List<MessageAttachment>
 * 来源：AnswerPolishNode
 */
public static final String ATTACHMENTS = "attachments";

/**
 * TTS 音频 URL
 * 类型：String
 * 来源：TouristController 调用 TtsService 后注入
 */
public static final String VOICE_URL = "voiceUrl";

/**
 * 音频时长（毫秒）
 * 类型：Integer
 * 来源：TtsService 返回
 */
public static final String VOICE_DURATION_MS = "voiceDurationMs";
```

---

## Phase 2: TTS 服务

### Task 5: TtsService 接口与 DashScope 实现

**Files:**
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/service/TtsService.java`
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/service/impl/DashScopeTtsServiceImpl.java`
- Modify: `jingbanyou-admin/src/main/resources/application.yml`（如需配置）

- [ ] **Step 1: TtsService.java**

```java
package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.tourist.dto.VoiceData;

public interface TtsService {
    /**
     * 将文本转为语音，返回音频 URL
     * @param text 待合成文本
     * @param voiceCode 音色代码（来自 DigitalHumanConfig.ttsVoiceCode）
     * @return VoiceData 含 audioUrl 和 durationMs
     */
    VoiceData synthesize(String text, String voiceCode);
}
```

- [ ] **Step 2: DashScopeTtsServiceImpl.java**

DashScope TTS 通过 `DashScopeSpeechModel` 调用阿里云语音合成 API。实现逻辑：

```java
package cn.edu.gdou.jingbanyou.tourist.service.impl;

import cn.edu.gdou.jingbanyou.tourist.dto.VoiceData;
import cn.edu.gdou.jingbanyou.tourist.service.TtsService;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.models.AudioSpeechTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
public class DashScopeTtsServiceImpl implements TtsService {

    @Autowired
    private DashScopeApi dashScopeApi;

    @Value("${jingbanyou.tts.audio-dir:/tmp/tts}")
    private String audioDir;

    @Override
    public VoiceData synthesize(String text, String voiceCode) {
        try {
            // 调用 DashScope TTS API
            AudioSpeechTask task = AudioSpeechTask.builder()
                    .model("cosyvoice-v1")          // 或 dashscope-tts
                    .input(text)
                    .voice(voiceCode != null ? voiceCode : "aixia")
                    .build();

            AudioSpeechTask.Result result = dashScopeApi.audioSpeech(task);
            byte[] audioBytes = result.getAudioData();

            // 保存到本地，提供 HTTP 可访问的 URL
            Path audioPath = Path.of(audioDir, UUID.randomUUID() + ".wav");
            Files.createDirectories(audioPath.getParent());
            Files.write(audioPath, audioBytes);

            String audioUrl = "/tts/" + audioPath.getFileName();

            // 估算时长（粗略：每字符约 200ms）
            int durationMs = text.length() * 200;

            log.info("TTS 合成成功，audioUrl={}, textLen={}", audioUrl, text.length());
            return VoiceData.builder()
                    .audioUrl(audioUrl)
                    .durationMs(durationMs)
                    .build();
        } catch (Exception e) {
            log.error("TTS 合成失败，text={}", text, e);
            return null;
        }
    }
}
```

> **注意：** `DashScopeApi` 需要 spring-ai-alibaba-starter-dashscope 依赖（已存在于 pom.xml）。TTS 具体 API 类名需对照 spring-ai-alibaba 实际版本（v1.x 中为 `DashScopeApi.audioSpeech()`）。

---

## Phase 3: Graph 改造（新增 AnswerPolishNode + SpotRetrievalNode）

### Task 6: AnswerPolishNode — AI 生成回答 + 附件

**Files:**
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/graph/node/AnswerPolishNode.java`
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/graph/node/SpotRetrievalNode.java`
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/constant/GraphNodeNames.java`
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/graph/GraphConfiguration.java`

- [ ] **Step 1: 在 GraphNodeNames.java 添加新节点名常量**

```java
/**
 * 景点数据库检索节点
 * 功能：从 MySQL 检索景点数据，构建景点卡片
 * 输入：QUESTION, SCENIC_ID
 * 输出：ATTACHMENTS（SpotAttachment）
 */
public static final String SPOT_RETRIEVAL = "spotRetrieval";

/**
 * 回答润色节点（生成回答 + 结构化附件）
 * 功能：基于 LLM 生成回答，并从回答中提取/生成路线卡片或景点卡片
 * 输入：ANSWER（已有回答）, RAW_ROUTES, RETRIEVED_DOCS, VISITOR_PROFILE
 * 输出：ANSWER（润色后）, ATTACHMENTS
 */
public static final String ANSWER_POLISH = "answerPolish";
```

- [ ] **Step 2: SpotRetrievalNode.java**

```java
package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import cn.edu.gdou.jingbanyou.manage.entity.ScenicSpot;
import cn.edu.gdou.jingbanyou.manage.entity.TourRoute;
import cn.edu.gdou.jingbanyou.manage.mapper.ScenicSpotMapper;
import cn.edu.gdou.jingbanyou.manage.mapper.TourRouteMapper;
import cn.edu.gdou.jingbanyou.tourist.dto.attachment.MessageAttachment;
import cn.edu.gdou.jingbanyou.tourist.dto.attachment.SpotAttachment;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class SpotRetrievalNode implements NodeAction {

    private final ScenicSpotMapper scenicSpotMapper;
    private final TourRouteMapper tourRouteMapper;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(QUESTION, String.class).orElse("");
        Long scenicId = state.value(SCENIC_ID, Long.class).orElse(0L);

        List<MessageAttachment> attachments = new ArrayList<>();

        // 检索景点数据
        List<ScenicSpot> spots = scenicSpotMapper.selectList(
                new LambdaQueryWrapper<ScenicSpot>()
                        .eq(ScenicSpot::getScenicId, scenicId)
                        .eq(ScenicSpot::getStatus, 1)
                        .orderByDesc(ScenicSpot::getSort)
                        .last("LIMIT 3")
        );

        for (ScenicSpot spot : spots) {
            // 简单关键词匹配：如果问题包含景点名关键词，则生成景点卡片
            if (question.contains(spot.getSpotName())
                    || spot.getSpotName().contains(extractKeyword(question))) {

                List<SpotAttachment.SpotMetric> metrics = new ArrayList<>();
                if (spot.getVisitDuration() != null) {
                    metrics.add(new SpotAttachment.SpotMetric("建议时长", spot.getVisitDuration() + " 分钟"));
                }
                if (spot.getSuitableSeason() != null) {
                    metrics.add(new SpotAttachment.SpotMetric("适宜季节", spot.getSuitableSeason()));
                }

                SpotAttachment card = SpotAttachment.builder()
                        .id("spot-" + spot.getId())
                        .type("spot")
                        .eyebrow("景点介绍")
                        .title(spot.getSpotName())
                        .description(spot.getSpotDesc() != null ? spot.getSpotDesc() : "")
                        .metrics(metrics)
                        .build();
                attachments.add(card);
            }
        }

        // 检索路线数据（如果涉及路线规划意图）
        String intent = state.value(INTENT, String.class).orElse("");
        if (intent.contains("route") && scenicId > 0) {
            List<TourRoute> routes = tourRouteMapper.selectList(
                    new LambdaQueryWrapper<TourRoute>()
                            .eq(TourRoute::getScenicId, scenicId)
                            .eq(TourRoute::getStatus, 1)
                            .orderByDesc(TourRoute::getSort)
                            .last("LIMIT 3")
            );

            for (TourRoute route : routes) {
                attachments.add(buildRouteAttachment(route));
            }
        }

        return state.updateState(Map.of(ATTACHMENTS, attachments));
    }

    private String extractKeyword(String question) {
        // 简化：取问题前6个字作为关键词
        return question.length() > 6 ? question.substring(0, 6) : question;
    }

    private MessageAttachment buildRouteAttachment(TourRoute route) {
        List<RouteAttachment.RouteItem> items = new ArrayList<>();
        items.add(RouteAttachment.RouteItem.builder()
                .id("route-" + route.getId())
                .title(route.getRouteName())
                .summary(route.getRouteDesc() != null ? route.getRouteDesc() : "")
                .duration(route.getRouteTime() != null ? route.getRouteTime() + " 分钟" : "")
                .tags(route.getRouteTags() != null ? route.getRouteTags() : List.of())
                .build());

        return RouteAttachment.builder()
                .id("routes-" + route.getId())
                .type("routes")
                .eyebrow("路线推荐")
                .title(route.getRouteName())
                .meta(route.getRouteTime() != null ? "预计 " + route.getRouteTime() + " 分钟" : "")
                .items(items)
                .build();
    }
}
```

> **注意：** 需要 jingbanyou-tourist 依赖 jingbanyou-manage 的 Mapper。jingbanyou-tourist pom.xml 已有 `<dependency><artifactId>jingbanyou-manage</artifactId></dependency>`，可以直接引用。

- [ ] **Step 3: AnswerPolishNode.java**

该节点接收已生成的 ANSWER（纯文本），再调用 LLM 将回答润色并同时生成结构化附件。

```java
package cn.edu.gdou.jingbanyou.tourist.graph.node;

import static cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey.*;

import cn.edu.gdou.jingbanyou.tourist.dto.attachment.*;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerPolishNode implements NodeAction {

    @Qualifier("generalChatChatClient")
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String answer = state.value(ANSWER, String.class).orElse("");
        String question = state.value(QUESTION, String.class).orElse("");
        List<MessageAttachment> existingAttachments = state.value(ATTACHMENTS, new TypeReference<List<MessageAttachment>>() {})
                .orElse(new ArrayList<>());

        if (answer.isBlank() && existingAttachments.isEmpty()) {
            return state.updateState(Map.of(
                    ANSWER, "抱歉，我暂时没有找到相关的信息，建议您咨询景区工作人员。",
                    ATTACHMENTS, new ArrayList<>()
            ));
        }

        // 调用 LLM 润色回答并生成结构化附件
        String llmOutput = chatClient.prompt()
                .user(userSpec -> userSpec.text(
                        "请将以下AI回答润色为亲切自然的数字人语气，并从中提取或生成结构化附件卡片。\n\n" +
                        "用户问题：" + question + "\n" +
                        "AI回答：" + answer + "\n\n" +
                        "要求：\n" +
                        "1. 润色后的回答要亲切、自然，像景区导游说话\n" +
                        "2. 如果回答中提到了具体景点，生成景点卡片（SpotAttachment）\n" +
                        "3. 如果回答中涉及游览建议，生成路线卡片（RouteAttachment）\n" +
                        "4. 返回JSON格式：{\"content\": \"润色后的回答\", \"attachments\": [附件数组]}\n" +
                        "5. 如果没有可提取的信息，attachments 返回空数组\n" +
                        "6. content 不能为空字符串\n\n" +
                        "返回示例：\n" +
                        "{\"content\": \"始信峰海拔1680米，景色非常秀丽...\", " +
                        "\"attachments\": [{\"id\":\"spot-1\",\"type\":\"spot\",\"eyebrow\":\"景点介绍\",\"title\":\"始信峰\",\"description\":\"...\",\"metrics\":[{\"label\":\"海拔\",\"value\":\"1680m\"}]}]}"
                ))
                .call()
                .content();

        try {
            String cleaned = llmOutput.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            Map<String, Object> parsed = objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});

            String polishedContent = (String) parsed.getOrDefault("content", answer);
            List<?> rawAttachments = (List<?>) parsed.getOrDefault("attachments", new ArrayList<>());
            List<MessageAttachment> attachments = parseAttachments(rawAttachments, existingAttachments);

            log.info("AnswerPolishNode 完成，contentLen={}, attachments={}", polishedContent.length(), attachments.size());

            return state.updateState(Map.of(
                    ANSWER, polishedContent,
                    ATTACHMENTS, attachments
            ));
        } catch (Exception e) {
            log.warn("AnswerPolishNode 解析失败，使用原始回答，error={}", e.getMessage());
            return state.updateState(Map.of(
                    ANSWER, answer,
                    ATTACHMENTS, existingAttachments
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private List<MessageAttachment> parseAttachments(List<?> raw, List<MessageAttachment> fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try {
            List<MessageAttachment> result = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) item;
                    String type = (String) map.get("type");
                    if ("routes".equals(type)) {
                        result.add(objectMapper.convertValue(map, RouteAttachment.class));
                    } else if ("spot".equals(type)) {
                        result.add(objectMapper.convertValue(map, SpotAttachment.class));
                    }
                }
            }
            return result.isEmpty() ? fallback : result;
        } catch (Exception e) {
            return fallback;
        }
    }
}
```

- [ ] **Step 4: 修改 GraphConfiguration.java — 注册新节点并调整路由**

在 `compiledGraph()` 方法中：

1. 注册新节点：
```java
stateGraph.addNode(ANSWER_POLISH, AsyncNodeAction.node_async(answerPolishNode));
stateGraph.addNode(SPOT_RETRIEVAL, AsyncNodeAction.node_async(spotRetrievalNode));
```

2. 修改景点问答路径（核心变化）：
```java
// 旧：
stateGraph.addEdge(SCENIC_KNOWLEDGE_RETRIEVAL, SCENIC_KNOWLEDGE_ANSWER_GENERATOR);
stateGraph.addEdge(SCENIC_KNOWLEDGE_ANSWER_GENERATOR, PROFILE_UPDATER);

// 新：
stateGraph.addEdge(SCENIC_KNOWLEDGE_RETRIEVAL, SCENIC_KNOWLEDGE_ANSWER_GENERATOR);
stateGraph.addEdge(SCENIC_KNOWLEDGE_ANSWER_GENERATOR, ANSWER_POLISH);
stateGraph.addEdge(ANSWER_POLISH, SPOT_RETRIEVAL);
stateGraph.addEdge(SPOT_RETRIEVAL, PROFILE_UPDATER);
stateGraph.addEdge(PROFILE_UPDATER, StateGraph.END);
```

3. 修改闲聊兜底路径：
```java
// 旧：
stateGraph.addEdge(GENERAL_CHAT_FALLBACK, PROFILE_UPDATER);

// 新：
stateGraph.addEdge(GENERAL_CHAT_FALLBACK, ANSWER_POLISH);
```

4. 路线规划路径（直接结束，不再经过 AnswerPolish）：
```java
// 路线规划 → 润色 → 画像更新 → 结束
stateGraph.addEdge(MAP_ROUTE_API_INVOKER, ROUTE_POLISH);
stateGraph.addEdge(ROUTE_POLISH, SPOT_RETRIEVAL);
stateGraph.addEdge(SPOT_RETRIEVAL, PROFILE_UPDATER);
stateGraph.addEdge(PROFILE_UPDATER, StateGraph.END);
```

> **注意：** 删除旧的 `stateGraph.addEdge(PROFILE_UPDATER, StateGraph.END);` 行。

---

## Phase 4: TouristController（统一前台入口）

### Task 7: TouristController 实现

**Files:**
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/controller/TouristController.java`
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/config/TouristWebConfig.java`（CORS + 静态资源）
- Create: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/controller/TtsAudioController.java`（TTS 音频文件访问）
- Modify: `jingbanyou-tourist/src/main/java/cn/edu/gdou/jingbanyou/tourist/controller/ChatController.java`

- [ ] **Step 1: TouristController.java**

```java
package cn.edu.gdou.jingbanyou.tourist.controller;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.manage.entity.ScenicArea;
import cn.edu.gdou.jingbanyou.manage.service.IDigitalHumanConfigService;
import cn.edu.gdou.jingbanyou.manage.service.IScenicAreaService;
import cn.edu.gdou.jingbanyou.tourist.dto.ChatRequest;
import cn.edu.gdou.jingbanyou.tourist.dto.ChatResponse;
import cn.edu.gdou.jingbanyou.tourist.dto.VoiceData;
import cn.edu.gdou.jingbanyou.tourist.dto.attachment.MessageAttachment;
import cn.edu.gdou.jingbanyou.tourist.graph.GraphConfiguration;
import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import cn.edu.gdou.jingbanyou.tourist.service.TtsService;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/tourist")
@RequiredArgsConstructor
public class TouristController {

    private final IScenicAreaService scenicAreaService;
    private final IDigitalHumanConfigService digitalHumanService;
    private final GraphConfiguration graphConfiguration;
    private final TtsService ttsService;

    // ==================== /api/tourist/bootstrap ====================

    @GetMapping("/bootstrap")
    public Map<String, Object> bootstrap(@RequestParam Long scenicId) {
        // 1. 景区数据
        ScenicArea area = scenicAreaService.getById(scenicId);
        if (area == null) {
            return Map.of("code", 404, "msg", "景区不存在");
        }

        // 2. 默认数字人
        DigitalHumanConfig digitalHuman = digitalHumanService.getDefaultByScenicId(scenicId);

        // 3. 欢迎语
        List<Map<String, String>> conversation = new ArrayList<>();
        String greeting = digitalHuman != null && digitalHuman.getDefaultGreeting() != null
                ? digitalHuman.getDefaultGreeting()
                : "欢迎来到" + area.getScenicName() + "，我是您的AI导游。";
        conversation.add(Map.of(
                "id", "assistant-welcome",
                "role", "assistant",
                "content", greeting
        ));

        // 4. 组装响应
        Map<String, Object> scenicVO = new LinkedHashMap<>();
        scenicVO.put("scenicId", area.getId());
        scenicVO.put("scenicName", area.getScenicName());
        scenicVO.put("scenicDesc", area.getScenicDesc());
        scenicVO.put("coverImage", area.getCoverImage());
        scenicVO.put("topFeatures", area.getTopFeatures() != null ? area.getTopFeatures() : List.of());
        scenicVO.put("quickPrompts", area.getQuickPrompts() != null ? area.getQuickPrompts() : List.of());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenic", scenicVO);
        result.put("digitalHuman", digitalHuman);
        result.put("conversation", conversation);

        return Map.of("code", 200, "msg", "操作成功", "data", result);
    }

    // ==================== /api/tourist/chat ====================

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        try {
            // 1. 构建初始状态
            Map<String, Object> initialState = new HashMap<>();
            initialState.put(GraphStateKey.SESSION_ID, request.getSessionId());
            initialState.put(GraphStateKey.QUESTION, request.getMessage() != null ? request.getMessage() : "");
            initialState.put(GraphStateKey.HISTORY, "");
            initialState.put(GraphStateKey.LANGUAGE, request.getLanguage() != null ? request.getLanguage() : "zh");
            initialState.put(GraphStateKey.SCENIC_ID, request.getScenicId());

            if (request.getAudioData() != null && request.getAudioData().length > 0) {
                initialState.put(GraphStateKey.AUDIO_DATA, request.getAudioData());
            }

            // 2. 执行 Graph
            CompiledGraph graph = graphConfiguration.compiledGraph();
            OverAllState resultState = graph.invoke(initialState);

            String answer = resultState.value(GraphStateKey.ANSWER).orElse("");
            String intent = resultState.value(GraphStateKey.INTENT).orElse("");
            String voiceUrl = resultState.value(GraphStateKey.VOICE_URL).orElse(null);

            // 3. 获取附件
            List<MessageAttachment> attachments = resultState.value(
                    GraphStateKey.ATTACHMENTS, new com.fasterxml.jackson.core.type.TypeReference<List<MessageAttachment>>() {})
                    .orElse(new ArrayList<>());

            // 4. 构建回复
            String replyId = "assistant-" + System.currentTimeMillis();
            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("id", replyId);
            reply.put("role", "assistant");
            reply.put("content", answer);
            reply.put("attachments", attachments);

            // 5. 构建响应
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("reply", reply);
            if (voiceUrl != null) {
                response.put("voice", VoiceData.builder()
                        .audioUrl(voiceUrl)
                        .durationMs(resultState.value(GraphStateKey.VOICE_DURATION_MS, Integer.class).orElse(0))
                        .build());
            } else {
                response.put("voice", null);
            }

            log.info("Chat 请求处理完成，sessionId={}, answerLen={}, attachments={}",
                    request.getSessionId(), answer.length(), attachments.size());

            return Map.of("code", 200, "msg", "操作成功", "data", response);

        } catch (Exception e) {
            log.error("Chat 处理失败", e);
            return Map.of("code", 500, "msg", "处理失败: " + e.getMessage());
        }
    }

    // ==================== /api/tourist/chat (multipart，支持音频) ====================

    @PostMapping("/chat")
    public Map<String, Object> chatWithAudio(
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "scenicId", required = false) Long scenicId,
            @RequestParam(value = "language", defaultValue = "zh") String language
    ) {
        ChatRequest request = new ChatRequest();
        request.setSessionId(sessionId);
        request.setMessage(message);
        request.setScenicId(scenicId);
        request.setLanguage(language);
        if (audio != null) {
            try {
                request.setAudioData(audio.getBytes());
            } catch (Exception e) {
                log.error("读取音频失败", e);
            }
        }
        return chat(request);
    }

    // ==================== /api/tourist/tts ====================

    @PostMapping("/tts")
    public Map<String, Object> tts(@RequestBody Map<String, Object> body) {
        try {
            String text = (String) body.get("text");
            String voiceCode = (String) body.getOrDefault("voiceCode", "aixia");

            if (text == null || text.isBlank()) {
                return Map.of("code", 400, "msg", "text 不能为空");
            }

            VoiceData voice = ttsService.synthesize(text, voiceCode);
            if (voice == null) {
                return Map.of("code", 500, "msg", "TTS 合成失败");
            }

            return Map.of("code", 200, "msg", "操作成功", "data", voice);

        } catch (Exception e) {
            log.error("TTS 处理失败", e);
            return Map.of("code", 500, "msg", "处理失败: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 2: TtsAudioController.java（TTS 音频文件静态访问）**

```java
package cn.edu.gdou.jingbanyou.tourist.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/tts")
public class TtsAudioController {

    @Value("${jingbanyou.tts.audio-dir:/tmp/tts}")
    private String audioDir;

    @GetMapping("/{filename}")
    public Resource getAudio(@PathVariable String filename) throws Exception {
        Path file = Paths.get(audioDir).resolve(filename);
        return new UrlResource(file.toUri());
    }
}
```

- [ ] **Step 3: TouristWebConfig.java（CORS + 静态资源）**

```java
package cn.edu.gdou.jingbanyou.tourist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TouristWebConfig implements WebMvcConfigurer {

    @Value("${jingbanyou.tts.audio-dir:/tmp/tts}")
    private String audioDir;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // TTS 音频文件通过 /tts/** 路径访问
        registry.addResourceHandler("/tts/**")
                .addResourceLocations("file:" + audioDir + "/");
    }
}
```

- [ ] **Step 4: 标记 ChatController 旧接口废弃**

在 `ChatController.java` 类名上方添加 `@Deprecated` 注解，或在每个方法上添加：

```java
/**
 * @deprecated 已被 TouristController 统一入口取代
 */
@Deprecated
@RestController
@RequestMapping("/tourist/chat")
public class ChatController {
    // 保持原有实现不变
}
```

---

## Phase 5: 配置

### Task 8: 配置文件

**Files:**
- Create: `jingbanyou-tourist/src/main/resources/application.yml`
- Modify: `jingbanyou-admin/src/main/resources/application.yml`

- [ ] **Step 1: jingbanyou-tourist/src/main/resources/application.yml**

```yaml
server:
  port: 8082

spring:
  application:
    name: jingbanyou-tourist

jingbanyou:
  tts:
    audio-dir: /tmp/tts  # TTS 生成的音频文件存放目录，需确保可写
  ai:
    # 各 ChatClient 配置（已有）
    general-chat:
      prompt: "..."
      model:
        name: qwen-plus
        temperature: 0.8
        maxTokens: 1000
```

> **重要：** jingbanyou-tourist 作为独立模块，需要自己的 application.yml 配置文件。需要在 RuoYi 的 `pom.xml` 中确认 jingbanyou-tourist 是否作为独立服务启动。如果是嵌入启动，则共享 jingbanyou-admin 的配置。

---

## Task 汇总

| # | 任务 | 文件 | 复杂度 |
|---|------|------|--------|
| 1 | ChatRequest/ChatResponse DTO | `dto/ChatRequest.java`, `dto/ChatResponse.java` | 低 |
| 2 | 附件 DTO（RouteAttachment/SpotAttachment） | `dto/attachment/*.java`, `dto/VoiceData.java` | 低 |
| 3 | BootstrapVO | `dto/BootstrapVO.java` | 低 |
| 4 | GraphStateKey 新增 | `constant/GraphStateKey.java` | 低 |
| 5 | TtsService 接口 + DashScope 实现 | `service/TtsService.java`, `service/impl/DashScopeTtsServiceImpl.java` | 中 |
| 6 | Graph 改造（AnswerPolishNode + SpotRetrievalNode） | `graph/node/*.java`, `graph/GraphConfiguration.java` | 高 |
| 7 | TouristController 统一入口 | `controller/TouristController.java`, `controller/TtsAudioController.java` | 高 |
| 8 | 配置文件 | `resources/application.yml` | 低 |

---

## 注意事项

1. **TTS API 确认**：spring-ai-alibaba 的 DashScope TTS 具体类名需对照实际版本。主要使用 `DashScopeApi.audioSpeech()` 或 `DashScopeSpeechModel`。如版本不匹配，参考阿里云官方 SDK 文档。

2. **Mapper 依赖**：SpotRetrievalNode 使用了 `jingbanyou-manage` 的 Mapper。确认 jingbanyou-tourist 是否已依赖 jingbanyou-manage（pom.xml 已确认有依赖）。

3. **旧接口处理**：ChatController 标记 `@Deprecated` 即可，不需要立即删除，留给前端迁移时间。

4. **音频路径**：TTS 生成的音频文件需要前端可访问。建议部署时配置 Nginx 静态资源映射 `/tts/**` 到对应目录。

5. **RouteAttachment 导入**：AnswerPolishNode 中使用了 `RouteAttachment`，需要 import。

6. **Graph 路由调整**：GraphConfiguration 中需要删除所有 `stateGraph.addEdge(PROFILE_UPDATER, StateGraph.END);` 行，新增的路由需要确保 ProfileUpdater 之后直接到 END。
