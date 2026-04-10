package cn.edu.gdou.jingbanyou.tourist.controller;

import cn.edu.gdou.jingbanyou.tourist.graph.GraphConfiguration;
import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/tourist/chat")
@RequiredArgsConstructor
public class ChatController {

    private final GraphConfiguration graphConfiguration;

    /**
     * 音频 + 文字 交互
     * 音频直接传入 Graph，第一个节点（DistinguishNode）使用多模态模型处理
     *
     * @param audio     语音文件（multipart）
     * @param text      游客输入的文字（可选，优先级高于音频）
     * @param sessionId 会话ID
     * @param language  语言（zh/en）
     * @param scenicId  景区ID
     */
    @PostMapping("/audio")
    public Map<String, Object> audioChat(
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "language", defaultValue = "zh") String language,
            @RequestParam(value = "scenicId", required = false) Long scenicId
    ) {
        try {
            // 构建初始状态
            Map<String, Object> initialState = new HashMap<>();
            initialState.put(GraphStateKey.SESSION_ID, sessionId);
            initialState.put(GraphStateKey.QUESTION, text != null ? text : "");
            initialState.put(GraphStateKey.HISTORY, "");
            initialState.put(GraphStateKey.LANGUAGE, language);
            if (scenicId != null) {
                initialState.put(GraphStateKey.SCENIC_ID, scenicId);
            }

            // 如果有音频，将音频数据注入 State（DistinguishNode 会处理）
            if (audio != null) {
                initialState.put("audioData", audio.getBytes());
                log.info("接收到音频，大小={} bytes, 类型={}", audio.getSize(), audio.getContentType());
            }

            // 执行 Graph
            CompiledGraph graph = graphConfiguration.compiledGraph();
            OverAllState result = graph.invoke(initialState);

            return Map.of(
                    "code", 200,
                    "msg", "success",
                    "data", Map.of(
                            "answer", result.value(GraphStateKey.ANSWER).orElse(""),
                            "intent", result.value(GraphStateKey.INTENT).orElse("")
                    )
            );
        } catch (Exception e) {
            log.error("Graph 执行失败", e);
            return Map.of("code", 500, "msg", "处理失败: " + e.getMessage());
        }
    }

    /**
     * 纯文字交互
     */
    @PostMapping("/text")
    public Map<String, Object> textChat(
            @RequestBody Map<String, Object> request
    ) {
        try {
            String text = (String) request.get("text");
            String sessionId = (String) request.get("sessionId");
            Long scenicId = request.get("scenicId") != null
                    ? ((Number) request.get("scenicId")).longValue()
                    : null;

            Map<String, Object> initialState = new HashMap<>();
            initialState.put(GraphStateKey.SESSION_ID, sessionId);
            initialState.put(GraphStateKey.QUESTION, text != null ? text : "");
            initialState.put(GraphStateKey.HISTORY, "");
            initialState.put(GraphStateKey.LANGUAGE, "zh");
            if (scenicId != null) {
                initialState.put(GraphStateKey.SCENIC_ID, scenicId);
            }

            CompiledGraph graph = graphConfiguration.compiledGraph();
            OverAllState result = graph.invoke(initialState);

            return Map.of(
                    "code", 200,
                    "msg", "success",
                    "data", Map.of(
                            "answer", result.value(GraphStateKey.ANSWER).orElse(""),
                            "intent", result.value(GraphStateKey.INTENT).orElse("")
                    )
            );
        } catch (Exception e) {
            log.error("Graph 执行失败", e);
            return Map.of("code", 500, "msg", "处理失败: " + e.getMessage());
        }
    }
}
