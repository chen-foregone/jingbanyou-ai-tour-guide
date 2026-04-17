package cn.edu.gdou.jingbanyou.tourist.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Anonymous;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.manage.dto.ScenicAreaVO;
import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.manage.service.IDigitalHumanConfigService;
import cn.edu.gdou.jingbanyou.manage.service.IScenicAreaService;
import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import cn.edu.gdou.jingbanyou.tourist.graph.StreamGraphConfiguration;
import cn.edu.gdou.jingbanyou.tourist.service.ChatMemoryService;
import cn.edu.gdou.jingbanyou.tourist.service.TtsService;
import cn.edu.gdou.jingbanyou.tourist.service.TranscribeService;
import cn.hutool.core.bean.BeanUtil;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import cn.hutool.extra.emoji.EmojiUtil;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.Base64;

/**
 * 前台游客端
 *
 * @author jingbanyou
 */
@Slf4j
@RestController
@RequestMapping("/api/tourist")
@RequiredArgsConstructor
@Anonymous
public class TouristController extends BaseController {

    private final IScenicAreaService scenicAreaService;
    private final IDigitalHumanConfigService digitalHumanConfigService;
    private final StreamGraphConfiguration streamGraphConfiguration;
    private final TranscribeService transcribeService;
    private final ChatMemoryService chatMemoryService;
    private final TtsService ttsService;

    /**
     * 前台首屏初始化
     * @param scenicId 景区ID
     * @return 景区信息、数字人配置、欢迎语
     */
    @GetMapping("/bootstrap")
    public AjaxResult bootstrap(@RequestParam(required = false) Long scenicId) {
        if (scenicId == null) {
            return error("景区ID不能为空");
        }
        
        ScenicAreaVO scenic = BeanUtil.copyProperties(
                scenicAreaService.getById(scenicId), ScenicAreaVO.class);
        if (scenic == null) {
            return error("景区不存在");
        }

        DigitalHumanConfig digitalHuman = digitalHumanConfigService.getDefaultByScenicId(scenicId);

        String greeting = (digitalHuman != null && digitalHuman.getDefaultGreeting() != null)
                ? digitalHuman.getDefaultGreeting()
                : "欢迎来到" + scenic.getScenicName() + "，我是您的专属 AI 导游，可以为您介绍景点、规划路线、解答疑问。";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenic", scenic);
        result.put("digitalHuman", digitalHuman);
        result.put("conversation", List.of(Map.of(
                "id", "assistant-welcome",
                "role", "assistant",
                "content", greeting
        )));

        return success(result);
    }

    /**
     * 文本对话（直接返回答案）
     * Graph 节点直接执行业务逻辑，结果写入 state.ANSWER，Controller 直接返回
     */
    @PostMapping("/chat")
    public AjaxResult chat(@RequestBody Map<String, Object> request) {
        String message = (String) request.get("message");
        String sessionId = (String) request.get("sessionId");
        Long scenicId = request.get("scenicId") != null
                ? ((Number) request.get("scenicId")).longValue() : null;
        log.info("[Chat入口] message={}, sessionId={}, scenicId={}", message, sessionId, scenicId);

        if (message == null || message.isBlank()) {
            return error("消息内容不能为空");
        }
        if (sessionId == null || sessionId.isBlank()) {
            return error("sessionId 不能为空");
        }

        try {
            long totalStart = System.currentTimeMillis();
            long graphStart = System.currentTimeMillis();
            OverAllState result = executeGraph(message, sessionId, scenicId);
            long graphCost = System.currentTimeMillis() - graphStart;
            log.info("[Chat] Graph执行耗时: {}ms", graphCost);

            String answer = result.value(GraphStateKey.ANSWER, String.class).orElse("");
            String intent = (String) result.value(GraphStateKey.INTENT).orElse("");

            // pending 场景返回引导语
            String routeStatus = (String) result.value(GraphStateKey.ROUTE_STATUS).orElse(null);
            if ("pending".equals(routeStatus)) {
                String guideMessage = (String) result.value(GraphStateKey.GUIDE_MESSAGE)
                        .orElse("请告诉我您的起点和终点");
                return success(Map.of(
                        "reply", Map.of("id", "assistant-" + System.currentTimeMillis(),
                                "role", "assistant", "content", guideMessage),
                        "intent", intent,
                        "attachments", List.of()
                ));
            }

            // 调用 TTS
            String audioUrl = "";
            long ttsStart = System.currentTimeMillis();
            if (answer != null && !answer.isBlank()) {
                List<?> rawRoutes = null;
                if (StreamGraphConfiguration.INTENT_ROUTE_PLAN.equals(intent)) {
                    rawRoutes = result.value(GraphStateKey.POLISHED_ROUTES, List.class).orElse(null);
                }
                String ttsText = answer;
                if (StreamGraphConfiguration.INTENT_ROUTE_PLAN.equals(intent) && rawRoutes != null && !rawRoutes.isEmpty()) {
                    ttsText = buildRouteNarration(rawRoutes);
                }
                // 过滤 emoji，避免 CosyVoice 报 418 错误
                ttsText = ttsText != null ? EmojiUtil.removeAllEmojis(ttsText) : "";
                if (ttsText.length() > 500) {
                    ttsText = ttsText.substring(0, 500);
                }
                DigitalHumanConfig dh = scenicId != null
                        ? digitalHumanConfigService.getDefaultByScenicId(scenicId) : null;
                audioUrl = ttsService.synthesize(ttsText, dh);
            }
            long ttsCost = System.currentTimeMillis() - ttsStart;
            log.info("[TTS] 合成耗时: {}ms", ttsCost);

            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("id", "assistant-" + System.currentTimeMillis());
            reply.put("role", "assistant");
            reply.put("content", answer);

            if (StreamGraphConfiguration.INTENT_ROUTE_PLAN.equals(intent)) {
                List<?> rawRoutes = result.value(GraphStateKey.POLISHED_ROUTES, List.class).orElse(null);
                reply.put("attachments", rawRoutes != null && !rawRoutes.isEmpty()
                        ? buildRouteAttachments(rawRoutes) : List.of());
            } else {
                reply.put("attachments", List.of());
            }

            return success(Map.of(
                    "reply", reply,
                    "intent", intent,
                    "voice", Map.of("audioUrl", audioUrl),
                    "graphCostMs", graphCost,
                    "ttsCostMs", ttsCost,
                    "totalCostMs", System.currentTimeMillis() - totalStart
            ));

        } catch (Exception e) {
            log.error("Graph 执行失败 sessionId={}", sessionId, e);
            return error("处理失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildRouteAttachments(List<?> rawRoutes) {
        List<Map<String, Object>> attachments = new ArrayList<>();

        Map<String, Object> routeAttachment = new LinkedHashMap<>();
        routeAttachment.put("id", "routes-" + System.currentTimeMillis());
        routeAttachment.put("type", "routes");
        routeAttachment.put("eyebrow", "路线推荐");

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < rawRoutes.size(); i++) {
            Map<String, Object> route = (Map<String, Object>) rawRoutes.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "route-" + i);
            item.put("title", route.getOrDefault("description", route.getOrDefault("title", "推荐路线")));
            item.put("duration", route.getOrDefault("duration", ""));
            // summary
            StringBuilder summary = new StringBuilder();
            if (route.get("suitableFor") != null) {
                summary.append("适合：").append(route.get("suitableFor"));
            }
            if (route.get("tips") != null) {
                if (summary.length() > 0) summary.append(" | ");
                summary.append("提示：").append(route.get("tips"));
            }
            item.put("summary", summary.toString());
            // tags
            List<String> tags = new ArrayList<>();
            if (route.get("suitableFor") != null) tags.add(route.get("suitableFor").toString());
            if (route.get("strategy") != null) tags.add(route.get("strategy").toString());
            item.put("tags", tags);
            items.add(item);
        }

        routeAttachment.put("title", "为您推荐以下游览路线");
        routeAttachment.put("meta", items.size() + " 条路线");
        routeAttachment.put("items", items);
        attachments.add(routeAttachment);

        return attachments;
    }

    /**
     * 执行 Graph 并返回结果
     */
    private OverAllState executeGraph(String message, String sessionId, Long scenicId) throws Exception {
        Map<String, Object> initialState = new HashMap<>();
        initialState.put(GraphStateKey.SESSION_ID, sessionId);
        initialState.put(GraphStateKey.QUESTION, message);
        initialState.put(GraphStateKey.HISTORY, "");
        initialState.put(GraphStateKey.LANGUAGE, "zh");
        if (scenicId != null) {
            initialState.put(GraphStateKey.SCENIC_ID, scenicId);
        }
        CompiledGraph graph = streamGraphConfiguration.streamCompiledGraph();
        return graph.invoke(initialState)
                .orElseThrow(() -> new RuntimeException("Graph 执行返回空结果"));
    }

    /**
     * 流式对话接口
     * Graph 节点直接执行业务逻辑，结果写入 state.ANSWER，Controller 流式发送
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody Map<String, Object> request) {
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
            long graphStart = System.currentTimeMillis();
            OverAllState result = executeGraph(message, sessionId, scenicId);
            long graphCost = System.currentTimeMillis() - graphStart;
            log.info("[流式对话] Graph执行耗时: {}ms", graphCost);

            String intent = (String) result.value(GraphStateKey.INTENT).orElse("");
            String routeStatus = (String) result.value(GraphStateKey.ROUTE_STATUS).orElse(null);

            // pending 场景
            if ("pending".equals(routeStatus)) {
                String guideMessage = (String) result.value(GraphStateKey.GUIDE_MESSAGE)
                        .orElse("请告诉我您的起点和终点");
                long timestamp = System.currentTimeMillis();
                return Flux.just(
                        metadataSse(intent, null, graphCost, timestamp),
                        answerSse(guideMessage),
                        doneSse(timestamp)
                );
            }

            String answer = (String) result.value(GraphStateKey.ANSWER).orElse("");
            if (answer == null || answer.isBlank()) {
                long timestamp = System.currentTimeMillis();
                return Flux.just(
                        metadataSse(intent, null, graphCost, timestamp),
                        answerSse("抱歉，暂时无法生成回复。"),
                        doneSse(timestamp)
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
            return Flux.just(metadataSse(intent, rawRoutes, graphCost, timestamp))
                    .concatWith(Flux.defer(() -> Flux.just(answerSse(answer))))
                    .concatWith(Flux.defer(() -> streamAudio(answer, intent, rawRoutes, scenicId, timestamp)))
                    .concatWith(Flux.just(doneSse(timestamp)))
                    .doOnError(e -> log.error("[流式对话] 流发送失败", e));

        } catch (Exception e) {
            log.error("[流式对话] 执行失败", e);
            return Flux.just(errorSse("处理失败: " + e.getMessage()));
        }
    }

    private Flux<ServerSentEvent<String>> streamString(String text) {
        int chunkSize = 20;
        return Flux.range(0, (text.length() + chunkSize - 1) / chunkSize)
                .map(i -> text.substring(i * chunkSize, Math.min((i + 1) * chunkSize, text.length())))
                .filter(chunk -> !chunk.isEmpty())
                .map(this::answerSse);
    }

    private ServerSentEvent<String> answerSse(String content) {
        String data = "{\"content\":\"" + escapeJson(content) + "\"}";
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .event("answer")
                .data(data)
                .build();
    }

    private Flux<ServerSentEvent<String>> streamAudio(String answer, String intent, List<?> rawRoutes, Long scenicId, long startTimestamp) {
        // 路线规划时从 JSON 提取文本描述，避免过长 JSON
        String ttsText = answer;
        if (StreamGraphConfiguration.INTENT_ROUTE_PLAN.equals(intent) && rawRoutes != null && !rawRoutes.isEmpty()) {
            ttsText = buildRouteNarration(rawRoutes);
        }
        // 过滤 emoji，避免 CosyVoice 报 418 错误
        ttsText = ttsText != null ? EmojiUtil.removeAllEmojis(ttsText) : "";

        // 截断超长文本（CosyVoice 单次限制约 500 字）
        if (ttsText.length() > 500) {
            ttsText = ttsText.substring(0, 500);
        }

        DigitalHumanConfig digitalHuman = scenicId != null
                ? digitalHumanConfigService.getDefaultByScenicId(scenicId) : null;

        AtomicInteger seq = new AtomicInteger(0);
        final long audioStart = System.currentTimeMillis();
        log.info("[TTS-流式] 开始合成, audioStart={}", audioStart);

        return ttsService.streamAudio(ttsText, digitalHuman)
                .map(chunk -> {
                    int s = seq.incrementAndGet();
                    String base64 = Base64.getEncoder().encodeToString(chunk);
                    log.debug("[TTS-流式] 推送第{}个chunk, 大小={}B", s, chunk.length);
                    return audioSse(s, base64, audioStart);
                })
                .doOnComplete(() -> {
                    long cost = System.currentTimeMillis() - audioStart;
                    log.info("[TTS-流式] 全部推送完成, 共{}个chunk, 耗时={}ms", seq.get(), cost);
                })
                .doOnError(e -> log.error("[TTS-流式] 流失败，切换文件方式: {}", e.getMessage()));
    }

    private String buildRouteNarration(List<?> rawRoutes) {
        StringBuilder sb = new StringBuilder("为您推荐以下路线：");
        for (int i = 0; i < rawRoutes.size(); i++) {
            Map<String, Object> route = (Map<String, Object>) rawRoutes.get(i);
            String strategy = String.valueOf(route.getOrDefault("strategy", "路线" + (i + 1)));
            String desc = String.valueOf(route.getOrDefault("description", ""));
            sb.append(strategy).append("路线：").append(desc).append("。");
        }
        return sb.toString();
    }

    private ServerSentEvent<String> audioSse(int seq, String base64Chunk, long startTimestamp) {
        long audioCost = System.currentTimeMillis() - startTimestamp;
        long serverTime = System.currentTimeMillis();
        String data = "{\"seq\":" + seq + ",\"chunk\":\"" + base64Chunk + "\",\"audioCostMs\":" + audioCost + ",\"serverTime\":" + serverTime + "}";
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(serverTime))
                .event("audio")
                .data(data)
                .build();
    }

    private ServerSentEvent<String> metadataSse(String intent, List<?> attachments, long graphCost, long timestamp) {
        String data;
        if (attachments == null || attachments.isEmpty()) {
            data = "{\"intent\":\"" + escapeJson(intent) + "\",\"graphCostMs\":" + graphCost + ",\"timestamp\":" + timestamp + "}";
        } else {
            String attachmentsJson = buildAttachmentsJson(attachments);
            data = "{\"intent\":\"" + escapeJson(intent) + "\",\"attachments\":" + attachmentsJson + ",\"graphCostMs\":" + graphCost + ",\"timestamp\":" + timestamp + "}";
        }
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(timestamp))
                .event("metadata")
                .data(data)
                .build();
    }

    private String buildAttachmentsJson(List<?> rawRoutes) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rawRoutes.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, Object> route = (Map<String, Object>) rawRoutes.get(i);
            sb.append("{");
            sb.append("\"id\":\"").append("route-").append(i).append("\",");
            sb.append("\"title\":\"").append(escapeJson(
                    String.valueOf(route.getOrDefault("description", route.getOrDefault("title", "推荐路线"))))).append("\",");
            sb.append("\"summary\":\"").append(escapeJson(buildRouteSummary(route))).append("\",");
            sb.append("\"duration\":\"").append(escapeJson(String.valueOf(route.getOrDefault("duration", "")))).append("\"");
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String buildRouteSummary(Map<String, Object> route) {
        StringBuilder sb = new StringBuilder();
        if (route.get("suitableFor") != null) {
            sb.append("适合：").append(route.get("suitableFor"));
        }
        if (route.get("tips") != null) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("提示：").append(route.get("tips"));
        }
        return sb.toString();
    }

    private ServerSentEvent<String> doneSse(long startTimestamp) {
        long totalCost = System.currentTimeMillis() - startTimestamp;
        String data = "{\"content\":\"\",\"totalCostMs\":" + totalCost + "}";
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .event("done")
                .data(data)
                .build();
    }

    private ServerSentEvent<String> errorSse(String message) {
        String data = "{\"content\":\"\",\"error\":\"" + escapeJson(message) + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .event("error")
                .data(data)
                .build();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 语音转文字
     * @param file 音频文件
     * @param language 语言提示
     * @return 识别文本
     */
    @PostMapping("/voice/transcribe")
    public AjaxResult transcribe(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "language", required = false, defaultValue = "zh") String language) {
        if (file == null || file.isEmpty()) {
            return error("音频文件不能为空");
        }

        try {
            byte[] audioData = file.getBytes();
            String text = transcribeService.transcribe(audioData, file.getOriginalFilename(), language);

            if (text.isEmpty()) {
                return error("语音识别失败");
            }

            return success(Map.of("text", text));

        } catch (Exception e) {
            log.error("ASR 处理失败", e);
            return error("语音识别失败: " + e.getMessage());
        }
    }

    /**
     * 会话结束，持久化对话历史
     * @param request 包含 sessionId、scenicId、visitorId
     * @return 保存结果
     */
    @PostMapping("/chat/end")
    public AjaxResult endChat(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        Object scenicIdObj = request.get("scenicId");
        String visitorId = (String) request.get("visitorId");

        if (sessionId == null || sessionId.isBlank()) {
            return error("sessionId 不能为空");
        }

        Long scenicId = null;
        if (scenicIdObj != null) {
            scenicId = scenicIdObj instanceof Number
                    ? ((Number) scenicIdObj).longValue()
                    : Long.parseLong(scenicIdObj.toString());
        }

        chatMemoryService.syncToMySQL(sessionId, scenicId, visitorId);
        return success("会话已保存");
    }

    /**
     * TTS 语音合成
     *
     * @param text      合成文本
     * @param scenicId  景区ID（可选，用于获取数字人配置）
     * @return 音频文件访问路径
     */
    @GetMapping("/tts")
    public AjaxResult tts(
            @RequestParam String text,
            @RequestParam(required = false) Long scenicId) {

        if (text == null || text.isBlank()) {
            return error("文本不能为空");
        }

        DigitalHumanConfig digitalHuman = null;
        if (scenicId != null) {
            digitalHuman = digitalHumanConfigService.getDefaultByScenicId(scenicId);
        }

        log.info("[TTS] text长度={}, voice={}",
                text.length(),
                digitalHuman != null ? digitalHuman.getTtsVoiceCode() : "默认");

        String audioUrl = ttsService.synthesize(text, digitalHuman);

        if (audioUrl == null || audioUrl.isBlank()) {
            return error("语音合成失败");
        }

        return success(Map.of("audioUrl", audioUrl));
    }

    /**
     * TTS 音频文件访问
     *
     * @param filename 音频文件名
     * @return 音频文件
     */
    @GetMapping("/tts/{filename}")
    public ResponseEntity<byte[]> ttsAudio(@PathVariable String filename) throws IOException {
        Path audioPath = Paths.get("/tmp/tts", filename);
        if (!Files.exists(audioPath)) {
            return ResponseEntity.notFound().build();
        }

        byte[] audioBytes = Files.readAllBytes(audioPath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(audioBytes);
    }
}
