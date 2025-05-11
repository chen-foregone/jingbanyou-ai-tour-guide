package cn.edu.gdou.jingbanyou.tourist.consumer;

import cn.edu.gdou.jingbanyou.tourist.config.RabbitMQConfig;
import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import cn.edu.gdou.jingbanyou.tourist.dto.ChatRequestMessage;
import cn.edu.gdou.jingbanyou.tourist.dto.StreamAnswerContext;
import cn.edu.gdou.jingbanyou.tourist.graph.StreamGraphConfiguration;
import cn.edu.gdou.jingbanyou.common.core.service.TouristSessionService;
import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.manage.service.IDigitalHumanConfigService;
import cn.edu.gdou.jingbanyou.tourist.service.IChatMetadataService;
import cn.edu.gdou.jingbanyou.tourist.service.IEmotionDetectService;
import cn.edu.gdou.jingbanyou.tourist.service.IStreamingAnswerService;
import cn.edu.gdou.jingbanyou.tourist.service.sse.SseEventFactory;
import cn.edu.gdou.jingbanyou.tourist.service.sse.SseResultPublisher;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 对话请求消费者
 * <p>
 * 监听 RabbitMQ 队列，执行 Graph，流式推送结果到 Redis
 *
 * @author jingbanyou
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRequestConsumer {

    private final StreamGraphConfiguration streamGraphConfiguration;
    private final IEmotionDetectService emotionDetectService;
    private final IChatMetadataService chatMetadataService;
    private final IDigitalHumanConfigService digitalHumanConfigService;
    private final TouristSessionService touristSessionService;
    private final IStreamingAnswerService streamingAnswerService;
    private final SseEventFactory sseEventFactory;
    private final SseResultPublisher resultPublisher;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.CHAT_QUEUE)
    public void handleChatRequest(ChatRequestMessage msg) {
        String conversationId = msg.conversationId();
        log.info("[Consumer] 收到请求 conversationId={}, message={}", conversationId, msg.message());

        try {
            // 1. 校验/创建访客会话
            touristSessionService.getOrCreateSession(msg.visitorId(), msg.scenicId(), null);

            // 2. 执行 Graph（阻塞 2-5 秒，在 Consumer 线程而非 Tomcat 线程）
            long graphStart = System.currentTimeMillis();
            OverAllState result = executeGraph(
                    msg.message(), msg.sessionId(), msg.scenicId(), msg.visitorId()
            );
            long graphCost = System.currentTimeMillis() - graphStart;

            // 3. 保存对话元数据
            String intent = (String) result.value(GraphStateKey.INTENT).orElse("");
            String retrievedDocs = result.value(GraphStateKey.RETRIEVED_DOCS, String.class).orElse("");
            Integer tokensUsed = (Integer) result.value(GraphStateKey.TOKENS_USED).orElse(null);
            String modelUsed = (String) result.value(GraphStateKey.MODEL_USED).orElse(null);
            String routeStatus = (String) result.value(GraphStateKey.ROUTE_STATUS).orElse(null);
            chatMetadataService.saveMetadata(msg.sessionId(), intent, tokensUsed, modelUsed, (int) graphCost, retrievedDocs);

            // 4. 发布 metadata 事件
            long timestamp = System.currentTimeMillis();
            List<?> rawRoutes = null;
            if (StreamGraphConfiguration.INTENT_ROUTE_PLAN.equals(intent)) {
                rawRoutes = result.value(GraphStateKey.POLISHED_ROUTES, List.class).orElse(null);
            }

            String metadataData = buildMetadataData(intent, rawRoutes, (int) graphCost, timestamp, msg.sessionId());
            resultPublisher.publishMetadata(conversationId, metadataData);

            // 5. pending 场景直接结束
            if ("pending".equals(routeStatus)) {
                String guideMessage = (String) result.value(GraphStateKey.GUIDE_MESSAGE)
                        .orElse("请告诉我您的起点和终点");
                resultPublisher.publishAnswer(conversationId, guideMessage);
                resultPublisher.publishDone(conversationId);
                return;
            }

            // 6. 无检索结果
            if (!StreamGraphConfiguration.INTENT_ROUTE_PLAN.equals(intent)
                    && (retrievedDocs == null || retrievedDocs.isBlank())) {
                resultPublisher.publishAnswer(conversationId, "抱歉，暂时无法生成回复。");
                resultPublisher.publishDone(conversationId);
                return;
            }

            // 7. 构建上下文并流式回答
            String originalQuestion = (String) result.value(GraphStateKey.QUESTION, String.class).orElse(msg.message());
            DigitalHumanConfig digitalHuman = null;
            if (msg.scenicId() != null) {
                digitalHuman = digitalHumanConfigService.getDefaultByScenicId(msg.scenicId());
            }
            Long humanId = digitalHuman != null ? digitalHuman.getId() : null;
            StreamAnswerContext ctx = new StreamAnswerContext(
                    msg.sessionId(), msg.scenicId(), humanId, msg.visitorId(),
                    intent, originalQuestion, retrievedDocs, digitalHuman,
                    rawRoutes, intent, (int) graphCost, timestamp
            );

            // 8. 订阅流式回答，每个事件写入 Redis
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

    /**
     * 执行 Graph 并返回结果
     */
    private OverAllState executeGraph(String message, String sessionId, Long scenicId, String visitorId) {
        Map<String, Object> initialState = new HashMap<>();
        initialState.put(GraphStateKey.SESSION_ID, sessionId);
        initialState.put(GraphStateKey.QUESTION, message);
        initialState.put(GraphStateKey.HISTORY, "");
        initialState.put(GraphStateKey.LANGUAGE, "zh");
        initialState.put(GraphStateKey.ROUTE_STATUS, "");
        initialState.put(GraphStateKey.GUIDE_MESSAGE, "");
        initialState.put(GraphStateKey.ANSWER, "");
        if (scenicId != null) {
            initialState.put(GraphStateKey.SCENIC_ID, scenicId);
        }
        if (visitorId != null && !visitorId.isBlank()) {
            initialState.put(GraphStateKey.VISITOR_ID, visitorId);
        }

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
     * 构建 metadata 事件的 data 部分 JSON
     */
    private String buildMetadataData(String intent, List<?> attachments,
                                     int graphCostMs, long timestamp, String sessionId) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "intent", intent,
                    "sessionId", sessionId != null ? sessionId : "",
                    "graphCostMs", graphCostMs,
                    "timestamp", timestamp,
                    "attachments", attachments != null ? attachments : java.util.List.of()
            ));
        } catch (JsonProcessingException e) {
            log.error("[Consumer] metadata JSON 构建失败", e);
            return "{}";
        }
    }
}
