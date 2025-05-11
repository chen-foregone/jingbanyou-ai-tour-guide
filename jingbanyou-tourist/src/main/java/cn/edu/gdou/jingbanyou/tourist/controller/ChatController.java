package cn.edu.gdou.jingbanyou.tourist.controller;

import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.core.service.TouristSessionService;
import cn.edu.gdou.jingbanyou.common.enums.TouristErrorCode;
import cn.edu.gdou.jingbanyou.common.utils.uuid.IdUtils;
import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.manage.service.IDigitalHumanConfigService;
import cn.edu.gdou.jingbanyou.tourist.config.RabbitMQConfig;
import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import cn.edu.gdou.jingbanyou.tourist.dto.ChatRequestMessage;
import cn.edu.gdou.jingbanyou.tourist.dto.StreamAnswerContext;
import cn.edu.gdou.jingbanyou.tourist.graph.StreamGraphConfiguration;
import cn.edu.gdou.jingbanyou.tourist.service.IChatMetadataService;
import cn.edu.gdou.jingbanyou.tourist.service.IConversationPersistenceService;
import cn.edu.gdou.jingbanyou.tourist.service.IEmotionDetectService;
import cn.edu.gdou.jingbanyou.tourist.service.IStreamingAnswerService;
import cn.edu.gdou.jingbanyou.tourist.service.sse.SseEventFactory;
import cn.edu.gdou.jingbanyou.tourist.service.sse.SseStreamBridge;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import io.micrometer.tracing.Tracer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * 对话核心
 *
 * @author jingbanyou
 */
@Slf4j
@RestController
@RequestMapping("/tourist")
@RequiredArgsConstructor
@Tag(name = "游客端-对话", description = "流式对话、异步对话、会话结束")
public class ChatController extends BaseController {

    private final StreamGraphConfiguration streamGraphConfiguration;
    private final IStreamingAnswerService streamingAnswerService;
    private final SseEventFactory sseEventFactory;
    private final IChatMetadataService chatMetadataService;
    private final IConversationPersistenceService conversationPersistenceService;
    private final IEmotionDetectService emotionDetectService;
    private final IDigitalHumanConfigService digitalHumanConfigService;
    private final TouristSessionService touristSessionService;
    private final RabbitTemplate rabbitTemplate;
    private final SseStreamBridge sseStreamBridge;
    private final Tracer tracer;

    /**
     * 流式对话接口
     *
     * 前端传入 visitorId + scenicId + message，可选传入 sessionId。
     * sessionId 由前端管理用于区分对话框，同一对话框内的多轮消息共享同一个 sessionId，
     * 这样 ChatMemory 能看到完整历史，路线规划等需要上下文的功能才能正常工作。
     * 不传 sessionId 则系统用雪花算法新建。
     * Graph 只做意图识别 + 检索，答案由 StreamingAnswerService 流式生成
     */
    @Operation(summary = "流式对话", description = "发送用户消息，通过 AI Graph 进行意图识别、RAG 检索和流式回答")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody Map<String, Object> request) {
        // 注入链路追踪 traceId 到 MDC，便于日志全链路关联
        String traceId = (tracer.currentSpan() != null)
                ? tracer.currentSpan().context().traceId()
                : "N/A";
        MDC.put("traceId", traceId);
        String message = (String) request.get("message");
        String visitorId;
        Object visitorIdObj = request.get("visitorId");
        if (visitorIdObj instanceof Number) {
            visitorId = String.valueOf(visitorIdObj);
        } else {
            visitorId = (String) visitorIdObj;
        }
        Long scenicId = request.get("scenicId") != null
                ? ((Number) request.get("scenicId")).longValue() : null;
        // 前端可传入 sessionId 以复用历史上下文；空则新建
        String sessionId = request.get("sessionId") instanceof String s && !s.isBlank()
                ? s
                : IdUtils.fastSimpleUUID();

        log.info("[流式对话] 开始: message={}, visitorId={}, scenicId={}, sessionId={}",
                message, visitorId, scenicId, sessionId);

        if (message == null || message.isBlank()) {
            return Flux.just(sseEventFactory.error(TouristErrorCode.T004.getMessage()));
        }
        if (visitorId == null || visitorId.isBlank()) {
            return Flux.just(sseEventFactory.error(TouristErrorCode.T005.getMessage()));
        }

        // 校验或创建访客会话（自动为首次访问的用户创建 Redis 会话，续期已有会话）
        touristSessionService.getOrCreateSession(visitorId, scenicId, null);

        try {
            long graphStart = System.currentTimeMillis();
            OverAllState result = executeGraph(message, sessionId, scenicId, visitorId);
            long graphCost = System.currentTimeMillis() - graphStart;
            log.info("[流式对话] Graph执行耗时: {}ms", graphCost);

            String intent = (String) result.value(GraphStateKey.INTENT).orElse("");
            String retrievedDocs = result.value(GraphStateKey.RETRIEVED_DOCS, String.class).orElse("");
            Integer tokensUsed = (Integer) result.value(GraphStateKey.TOKENS_USED).orElse(null);
            String modelUsed = (String) result.value(GraphStateKey.MODEL_USED).orElse(null);
            // 保存对话元数据到 Redis
            chatMetadataService.saveMetadata(sessionId, intent, tokensUsed, modelUsed, (int) graphCost, retrievedDocs);
            String routeStatus = (String) result.value(GraphStateKey.ROUTE_STATUS).orElse(null);

            // pending 场景
            if ("pending".equals(routeStatus)) {
                String guideMessage = (String) result.value(GraphStateKey.GUIDE_MESSAGE)
                        .orElse("请告诉我您的起点和终点");
                long timestamp = System.currentTimeMillis();
                return Flux.just(
                        sseEventFactory.metadata(intent, null, graphCost, timestamp, sessionId),
                        sseEventFactory.answer(guideMessage),
                        sseEventFactory.done(timestamp)
                );
            }

            if (!StreamGraphConfiguration.INTENT_ROUTE_PLAN.equals(intent)
                    && (retrievedDocs == null || retrievedDocs.isBlank())) {
                long timestamp = System.currentTimeMillis();
                return Flux.just(
                        sseEventFactory.metadata(intent, null, graphCost, timestamp, sessionId),
                        sseEventFactory.answer("抱歉，暂时无法生成回复。"),
                        sseEventFactory.done(timestamp)
                );
            }

            // 路线附件
            List<?> rawRoutes;
            if (StreamGraphConfiguration.INTENT_ROUTE_PLAN.equals(intent)) {
                rawRoutes = result.value(GraphStateKey.POLISHED_ROUTES, List.class).orElse(null);
            } else {
                rawRoutes = null;
            }

            long timestamp = System.currentTimeMillis();
            int graphCostMs = (int) graphCost;
            DigitalHumanConfig digitalHuman = getDigitalHumanConfig(scenicId);
            Long humanId = digitalHuman != null ? digitalHuman.getId() : null;

            StreamAnswerContext ctx = new StreamAnswerContext(
                    sessionId, scenicId, humanId, visitorId,
                    intent, message, retrievedDocs, digitalHuman,
                    rawRoutes, intent, graphCostMs, timestamp
            );

            return Flux.just(sseEventFactory.metadata(intent, rawRoutes, graphCost, timestamp, sessionId))
                    .concatWith(streamingAnswerService.streamAnswer(ctx))
                    .concatWith(Flux.just(sseEventFactory.done(timestamp)))
                    .doOnError(e -> log.error("[流式对话] 流发送失败", e));

        } catch (Exception e) {
            log.error("[流式对话] 执行失败", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return Flux.just(sseEventFactory.error(TouristErrorCode.T011.getMessage() + ": " + msg));
        }
    }

    /**
     * 提交对话请求（异步模式）
     * <p>
     * 前端传入 message + visitorId + scenicId，将请求发布到 RabbitMQ 队列后立即返回 conversationId。
     * 前端再用 conversationId 建立 SSE 连接获取实时结果。
     *
     * @param request 包含 message, visitorId, scenicId, sessionId
     * @return conversationId（用于订阅 SSE 结果流）
     */
    @Operation(summary = "提交对话请求（异步）", description = "将对话请求发布到消息队列，立即返回 conversationId，前端用此 ID 订阅 SSE 结果流")
    @PostMapping("/chat")
    public AjaxResult submitChat(@RequestBody Map<String, Object> request) {
        String message = (String) request.get("message");
        String visitorId = (String) request.get("visitorId");
        Long scenicId = request.get("scenicId") != null
                ? ((Number) request.get("scenicId")).longValue() : null;
        String sessionId = request.get("sessionId") instanceof String s && !s.isBlank()
                ? s : IdUtils.fastSimpleUUID();

        if (message == null || message.isBlank()) {
            return error(TouristErrorCode.T004);
        }
        if (visitorId == null || visitorId.isBlank()) {
            return error(TouristErrorCode.T005);
        }

        String conversationId = IdUtils.fastSimpleUUID();

        // 发布消息到 RabbitMQ
        ChatRequestMessage msg = new ChatRequestMessage(
                conversationId, message, sessionId, scenicId, visitorId
        );
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TOURIST_EXCHANGE,
                RabbitMQConfig.CHAT_ROUTING_KEY,
                msg
        );

        log.info("[提交对话] conversationId={}, message={}", conversationId, message);
        return success(Map.of("conversationId", conversationId, "sessionId", sessionId));
    }

    /**
     * 订阅 SSE 结果流（异步模式）
     * <p>
     * 前端通过 conversationId 订阅 Redis 中的 SSE 事件，实时接收 AI 回复段落和音频块。
     *
     * @param conversationId 对话请求 ID（由 /tourist/chat 返回）
     * @return SSE 事件流
     */
    @Operation(summary = "订阅 SSE 结果流", description = "通过 conversationId 订阅异步对话结果，支持流式文本段落和 TTS 音频块")
    @GetMapping(value = "/stream/{conversationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> subscribeStream(@PathVariable String conversationId) {
        log.info("[SSE订阅] conversationId={}", conversationId);
        return sseStreamBridge.createSseFlux(conversationId);
    }

    /**
     * 会话结束，持久化对话历史
     *
     * @param request 包含 sessionId、scenicId、visitorId
     * @return 保存结果
     */
    @Operation(summary = "结束会话", description = "将 Redis 中的对话历史和情感数据持久化到 MySQL")
    @PostMapping("/chat/end")
    public AjaxResult endChat(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        Object scenicIdObj = request.get("scenicId");
        String visitorId = (String) request.get("visitorId");
        String interactionType = (String) request.getOrDefault("interactionType", "text");

        if (sessionId == null || sessionId.isBlank()) {
            return error(TouristErrorCode.T006);
        }

        Long scenicId = null;
        if (scenicIdObj != null) {
            scenicId = scenicIdObj instanceof Number
                    ? ((Number) scenicIdObj).longValue()
                    : Long.parseLong(scenicIdObj.toString());
        }

        Object humanIdObj = request.get("humanId");
        doEndChat(sessionId, scenicId, visitorId, interactionType, humanIdObj);

        return success("会话已保存");
    }

    /**
     * 执行 Graph 并返回结果
     */
    private OverAllState executeGraph(String message, String sessionId, Long scenicId, String visitorId) {
        Map<String, Object> initialState = new HashMap<>();
        initialState.put(GraphStateKey.SESSION_ID, sessionId);
        initialState.put(GraphStateKey.QUESTION, message);
        initialState.put(GraphStateKey.HISTORY, "");
        initialState.put(GraphStateKey.LANGUAGE, "zh");
        // 显式重置路由状态，防止 Graph 框架层状态泄漏（用空字符串而非 null 避免序列化问题）
        initialState.put(GraphStateKey.ROUTE_STATUS, "");
        initialState.put(GraphStateKey.GUIDE_MESSAGE, "");
        initialState.put(GraphStateKey.ANSWER, "");
        if (scenicId != null) {
            initialState.put(GraphStateKey.SCENIC_ID, scenicId);
        }
        if (visitorId != null && !visitorId.isBlank()) {
            initialState.put(GraphStateKey.VISITOR_ID, visitorId);
        }

        // fire-and-forget：情感分析异步执行，不阻塞主流程
        emotionDetectService.detectEmotionAsync(sessionId, message, null);

        try {
            CompiledGraph graph = streamGraphConfiguration.streamCompiledGraph();
            return graph.invoke(initialState)
                    .orElseThrow(() -> new RuntimeException("Graph 执行返回空结果"));
        } catch (GraphStateException e) {
            throw new RuntimeException("Graph 执行异常: " + e.getMessage(), e);
        }
    }

    /**
     * 获取数字人配置
     */
    private DigitalHumanConfig getDigitalHumanConfig(Long scenicId) {
        if (scenicId == null) {
            return null;
        }
        return digitalHumanConfigService.getDefaultByScenicId(scenicId);
    }

    /**
     * 执行会话结束逻辑：读元数据→解析 humanId→syncToMySQL→读情感→updateEmotion→清理 Redis
     */
    private void doEndChat(String sessionId, Long scenicId, String visitorId,
                           String interactionType, Object humanIdObj) {
        // 从 Redis 取对话元数据
        Map<String, Object> meta = chatMetadataService.getMetadata(sessionId);
        String intentType = meta != null ? (String) meta.get("intent") : null;
        Integer responseTimeMs = meta != null ? (Integer) meta.get("responseTimeMs") : null;
        Integer tokensUsed = meta != null ? (Integer) meta.get("tokensUsed") : null;
        String modelUsed = meta != null ? (String) meta.get("modelUsed") : null;
        String ragDocs = meta != null ? (String) meta.get("ragDocs") : null;

        // 从 request 取 humanId（前端传入）
        Long humanId = null;
        if (humanIdObj != null) {
            humanId = humanIdObj instanceof Number
                    ? ((Number) humanIdObj).longValue()
                    : Long.parseLong(humanIdObj.toString());
        }
        // 兜底：通过 scenicId 查数字人配置
        if (humanId == null && scenicId != null) {
            DigitalHumanConfig config = digitalHumanConfigService.getDefaultByScenicId(scenicId);
            if (config != null) {
                humanId = config.getId();
            }
        }

        conversationPersistenceService.syncToMySQL(sessionId, scenicId, humanId, visitorId,
                interactionType, intentType, responseTimeMs, tokensUsed, modelUsed, ragDocs);

        // 读取情感数据并更新最近一条交互记录
        Map<String, Object> emotionData = emotionDetectService.getEmotionResult(sessionId);
        if (emotionData != null) {
            conversationPersistenceService.updateLastInteractionEmotion(sessionId,
                    (String) emotionData.get("emotion"),
                    emotionData.get("confidence") instanceof Number
                            ? ((Number) emotionData.get("confidence")).doubleValue() : null);
            emotionDetectService.deleteEmotionResult(sessionId);
        }

        // 清除元数据 key
        chatMetadataService.deleteMetadata(sessionId);
    }
}
