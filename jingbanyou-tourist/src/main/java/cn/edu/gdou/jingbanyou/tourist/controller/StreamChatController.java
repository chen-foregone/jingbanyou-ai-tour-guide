package cn.edu.gdou.jingbanyou.tourist.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Anonymous;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import cn.edu.gdou.jingbanyou.tourist.graph.GraphConfiguration;
import cn.edu.gdou.jingbanyou.tourist.graph.node.GeneralChatStreamNode;
import cn.edu.gdou.jingbanyou.tourist.graph.node.HybridRetrievalStreamNode;
import cn.edu.gdou.jingbanyou.tourist.graph.node.RoutePolishStreamNode;
import cn.edu.gdou.jingbanyou.tourist.graph.StreamGraphConfiguration;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 流式对话 Controller
 * 提供 /api/tourist/chat/stream 接口，返回 SSE 流式数据
 */
@Slf4j
@RestController
@RequestMapping("/api/tourist/chat")
@RequiredArgsConstructor
@Anonymous
public class StreamChatController extends BaseController {

    private final StreamGraphConfiguration streamGraphConfiguration;
    private final HybridRetrievalStreamNode hybridRetrievalStreamNode;
    private final GeneralChatStreamNode generalChatStreamNode;
    private final RoutePolishStreamNode routePolishStreamNode;

    /**
     * 流式对话接口
     * 流程：意图识别 → 业务处理 → LLM 流式生成答案
     *
     * @param request 包含 message、sessionId、scenicId
     * @return SSE 流式响应
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestBody Map<String, Object> request) {

        String message = (String) request.get("message");
        String sessionId = (String) request.get("sessionId");
        Long scenicId = request.get("scenicId") != null
                ? ((Number) request.get("scenicId")).longValue() : null;

        log.info("[流式对话] 开始: message={}, sessionId={}, scenicId={}", message, sessionId, scenicId);

        if (message == null || message.isBlank()) {
            return Flux.just(errorSse("消息内容不能为空"));
        }
        if (sessionId == null || sessionId.isBlank()) {
            return Flux.just(errorSse("sessionId 不能为空"));
        }

        try {
            // 1. 构建初始状态
            Map<String, Object> initialState = new HashMap<>();
            initialState.put(GraphStateKey.SESSION_ID, sessionId);
            initialState.put(GraphStateKey.QUESTION, message);
            initialState.put(GraphStateKey.HISTORY, "");
            initialState.put(GraphStateKey.LANGUAGE, "zh");
            if (scenicId != null) {
                initialState.put(GraphStateKey.SCENIC_ID, scenicId);
            }

            // 2. 执行 Graph（意图识别 + 业务处理，不包含最终答案）
            CompiledGraph graph = streamGraphConfiguration.streamCompiledGraph();
            OverAllState result = graph.invoke(initialState)
                    .orElseThrow(() -> new RuntimeException("Graph 执行返回空结果"));

            // 3. 根据意图选择流式节点
            String intent = (String) result.value(GraphStateKey.INTENT).orElse("");
            log.info("[流式对话] 意图={}", intent);

            // 构建 stateGetter 供流式节点使用
            Map<String, Object> finalState = new HashMap<>(result.data());

            // 缓存 answer 的完整内容（供 ProfileUpdaterNode 使用）
            final StringBuilder fullAnswer = new StringBuilder();

            return switch (intent) {
                case GraphConfiguration.INTENT_ROUTE_PLAN -> {
                    // 路线规划：先流式生成路线润色内容
                    yield routePolishStreamNode.polishAndStream(finalState::get)
                            .map(chunk -> {
                                fullAnswer.append(chunk);
                                return ServerSentEventBuilder.answer(chunk);
                            })
                            .concatWith(Flux.defer(() -> {
                                // 流结束后写入 ANSWER，供 ProfileUpdaterNode 使用
                                finalState.put(GraphStateKey.ANSWER, fullAnswer.toString());
                                return Flux.empty();
                            }))
                            .concatWith(Flux.just(ServerSentEventBuilder.done()));
                }
                case GraphConfiguration.INTENT_SPOT_QUESTION -> {
                    // 景点问答：流式生成答案
                    yield hybridRetrievalStreamNode.retrieveAndStream(finalState::get)
                            .map(chunk -> {
                                fullAnswer.append(chunk);
                                return ServerSentEventBuilder.answer(chunk);
                            })
                            .concatWith(Flux.defer(() -> {
                                finalState.put(GraphStateKey.ANSWER, fullAnswer.toString());
                                return Flux.empty();
                            }))
                            .concatWith(Flux.just(ServerSentEventBuilder.done()));
                }
                default -> {
                    // 闲聊兜底：流式生成闲聊回复
                    yield generalChatStreamNode.chatAndStream(finalState::get)
                            .map(chunk -> {
                                fullAnswer.append(chunk);
                                return ServerSentEventBuilder.answer(chunk);
                            })
                            .concatWith(Flux.defer(() -> {
                                finalState.put(GraphStateKey.ANSWER, fullAnswer.toString());
                                return Flux.empty();
                            }))
                            .concatWith(Flux.just(ServerSentEventBuilder.done()));
                }
            };

        } catch (Exception e) {
            log.error("[流式对话] 执行失败", e);
            return Flux.just(errorSse("处理失败: " + e.getMessage()));
        }
    }

    private ServerSentEvent<String> errorSse(String msg) {
        return ServerSentEventBuilder.error(msg);
    }

    /**
     * SSE 事件构建工具类
     */
    static class ServerSentEventBuilder {

        /**
         * 答案片段事件
         */
        public static ServerSentEvent<String> answer(String content) {
            String data = "{\"content\":\"" + escapeJson(content) + "\"}";
            return ServerSentEvent.<String>builder()
                    .id(String.valueOf(System.currentTimeMillis()))
                    .event("answer")
                    .data(data)
                    .build();
        }

        /**
         * 流结束事件
         */
        public static ServerSentEvent<String> done() {
            String data = "{\"content\":\"\"}";
            return ServerSentEvent.<String>builder()
                    .id(String.valueOf(System.currentTimeMillis()))
                    .event("done")
                    .data(data)
                    .build();
        }

        /**
         * 错误事件
         */
        public static ServerSentEvent<String> error(String message) {
            String data = "{\"content\":\"\",\"error\":\"" + escapeJson(message) + "\"}";
            return ServerSentEvent.<String>builder()
                    .id(String.valueOf(System.currentTimeMillis()))
                    .event("error")
                    .data(data)
                    .build();
        }

        private static String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
}
