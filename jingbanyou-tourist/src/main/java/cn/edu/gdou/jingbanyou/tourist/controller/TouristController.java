package cn.edu.gdou.jingbanyou.tourist.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Anonymous;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.manage.dto.ConversationDetailVO;
import cn.edu.gdou.jingbanyou.manage.dto.ConversationListVO;
import cn.edu.gdou.jingbanyou.manage.dto.ScenicAreaVO;
import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.manage.service.IDigitalHumanConfigService;
import cn.edu.gdou.jingbanyou.manage.service.IScenicAreaService;
import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import cn.edu.gdou.jingbanyou.tourist.graph.StreamGraphConfiguration;
import cn.edu.gdou.jingbanyou.tourist.service.IChatMemoryService;
import cn.edu.gdou.jingbanyou.tourist.service.IEmotionDetectService;
import cn.edu.gdou.jingbanyou.tourist.service.ITtsService;
import cn.edu.gdou.jingbanyou.tourist.service.ITranscribeService;
import cn.edu.gdou.jingbanyou.tourist.service.IVisitorConversationService;
import cn.edu.gdou.jingbanyou.tourist.service.IStreamingAnswerService;
import cn.hutool.core.bean.BeanUtil;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import cn.hutool.core.util.IdUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
    private final ITranscribeService transcribeService;
    private final IChatMemoryService chatMemoryService;
    private final ITtsService ttsService;
    private final IVisitorConversationService visitorConversationService;
    private final IEmotionDetectService emotionDetectService;
    private final IStreamingAnswerService streamingAnswerService;

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
     * 执行 Graph 并返回结果
     *
     * @param message   用户消息
     * @param sessionId 会话ID
     * @param scenicId  景区ID
     * @param visitorId 游客ID
     * @return Graph 执行结果
     */
    private OverAllState executeGraph(String message, String sessionId, Long scenicId, String visitorId) throws Exception {
        Map<String, Object> initialState = new HashMap<>();
        initialState.put(GraphStateKey.SESSION_ID, sessionId);
        initialState.put(GraphStateKey.QUESTION, message);
        initialState.put(GraphStateKey.HISTORY, "");
        initialState.put(GraphStateKey.LANGUAGE, "zh");
        if (scenicId != null) {
            initialState.put(GraphStateKey.SCENIC_ID, scenicId);
        }
        if (visitorId != null && !visitorId.isBlank()) {
            initialState.put(GraphStateKey.VISITOR_ID, visitorId);
        }

        // fire-and-forget：情感分析异步执行，不阻塞主流程
        emotionDetectService.detectEmotionAsync(sessionId, message, null);

        CompiledGraph graph = streamGraphConfiguration.streamCompiledGraph();
        return graph.invoke(initialState)
                .orElseThrow(() -> new RuntimeException("Graph 执行返回空结果"));
    }

    /**
     * 流式对话接口
     *
     * 前端传入 visitorId + scenicId + message，可选传入 sessionId。
     * sessionId 由前端管理用于区分对话框，同一对话框内的多轮消息共享同一个 sessionId，
     * 这样 ChatMemory 能看到完整历史，路线规划等需要上下文的功能才能正常工作。
     * 不传 sessionId 则系统用雪花算法新建。
     * Graph 只做意图识别 + 检索，答案由 StreamingAnswerService 流式生成
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody Map<String, Object> request) {
        String message = (String) request.get("message");
        String visitorId;
        Object visitorIdObj = request.get("visitorId");
        if (visitorIdObj instanceof Number) {
            visitorId = String.valueOf(visitorIdObj);
        } else {
            visitorId = (String) visitorIdObj;
        }
        Long scenicId = request. get("scenicId") != null
                ? ((Number) request.get("scenicId")).longValue() : null;
        // 前端可传入 sessionId 以复用历史上下文；空则新建
        String sessionId = request.get("sessionId") instanceof String s && !s.isBlank()
                ? s
                : IdUtil.getSnowflake().nextIdStr();

        log.info("[流式对话] 开始: message={}, visitorId={}, scenicId={}, sessionId={}",
                message, visitorId, scenicId, sessionId);

        if (message == null || message.isBlank()) {
            return Flux.just(errorSse("消息内容不能为空"));
        }
        if (visitorId == null || visitorId.isBlank()) {
            return Flux.just(errorSse("visitorId 不能为空"));
        }

        try {
            long graphStart = System.currentTimeMillis();
            OverAllState result = executeGraph(message, sessionId, scenicId, visitorId);
            long graphCost = System.currentTimeMillis() - graphStart;
            log.info("[流式对话] Graph执行耗时: {}ms", graphCost);

            String intent = (String) result.value(GraphStateKey.INTENT).orElse("");
            String retrievedDocs = result.value(GraphStateKey.RETRIEVED_DOCS, String.class).orElse("");
            Integer tokensUsed = (Integer) result.value(GraphStateKey.TOKENS_USED).orElse(null);
            String modelUsed = (String) result.value(GraphStateKey.MODEL_USED).orElse(null);
            String answer = (String) result.value(GraphStateKey.ANSWER).orElse("");
            // 保存对话元数据到 Redis
            chatMemoryService.saveChatMetadata(sessionId, intent, tokensUsed, modelUsed, (int) graphCost);
            // 记录单轮交互到 MySQL（spot_question/complex_other 的答案在流中才生成，此处传空）
            Long humanId = scenicId != null
                    ? digitalHumanConfigService.getDefaultByScenicId(scenicId).getId() : null;
            chatMemoryService.recordSingleTurn(sessionId, scenicId, humanId, visitorId,
                    message, StreamGraphConfiguration.INTENT_ROUTE_PLAN.equals(intent) ? (answer != null ? answer : "") : "",
                    "text", intent, (int) graphCost, tokensUsed, modelUsed);
            String routeStatus = (String) result.value(GraphStateKey.ROUTE_STATUS).orElse(null);

            // pending 场景
            if ("pending".equals(routeStatus)) {
                String guideMessage = (String) result.value(GraphStateKey.GUIDE_MESSAGE)
                        .orElse("请告诉我您的起点和终点");
                long timestamp = System.currentTimeMillis();
                return Flux.just(
                        metadataSse(intent, null, graphCost, timestamp, sessionId),
                        answerSse(guideMessage),
                        doneSse(timestamp)
                );
            }

            if (!StreamGraphConfiguration.INTENT_ROUTE_PLAN.equals(intent)
                    && (retrievedDocs == null || retrievedDocs.isBlank())) {
                long timestamp = System.currentTimeMillis();
                return Flux.just(
                        metadataSse(intent, null, graphCost, timestamp, sessionId),
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
            long startTimestamp = timestamp;
            int graphCostMs = (int) graphCost;
            DigitalHumanConfig digitalHuman = scenicId != null
                    ? digitalHumanConfigService.getDefaultByScenicId(scenicId) : null;

            return Flux.just(metadataSse(intent, rawRoutes, graphCost, timestamp, sessionId))
                    .concatWith(streamingAnswerService.streamAnswer(
                            sessionId, scenicId, humanId, visitorId,
                            intent, message, retrievedDocs, digitalHuman,
                            rawRoutes, intent, graphCostMs, startTimestamp
                    ))
                    .concatWith(Flux.just(doneSse(timestamp)))
                    .doOnError(e -> log.error("[流式对话] 流发送失败", e));

        } catch (Exception e) {
            log.error("[流式对话] 执行失败", e);
            return Flux.just(errorSse("处理失败: " + e.getMessage()));
        }
    }

    private ServerSentEvent<String> answerSse(String content) {
        String data = "{\"content\":\"" + escapeJson(content) + "\"}";
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(System.currentTimeMillis()))
                .event("answer")
                .data(data)
                .build();
    }

    private ServerSentEvent<String> metadataSse(String intent, List<?> attachments, long graphCost, long timestamp, String sessionId) {
        String data;
        String sid = escapeJson(sessionId != null ? sessionId : "");
        if (attachments == null || attachments.isEmpty()) {
            data = "{\"intent\":\"" + escapeJson(intent) + "\",\"sessionId\":\"" + sid + "\",\"graphCostMs\":" + graphCost + ",\"timestamp\":" + timestamp + "}";
        } else {
            String attachmentsJson = buildAttachmentsJson(attachments);
            data = "{\"intent\":\"" + escapeJson(intent) + "\",\"sessionId\":\"" + sid + "\",\"attachments\":" + attachmentsJson + ",\"graphCostMs\":" + graphCost + ",\"timestamp\":" + timestamp + "}";
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
     *
     * @param request 包含 sessionId、scenicId、visitorId
     * @return 保存结果
     */
    @PostMapping("/chat/end")
    public AjaxResult endChat(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        Object scenicIdObj = request.get("scenicId");
        String visitorId = (String) request.get("visitorId");
        String interactionType = (String) request.getOrDefault("interactionType", "text");

        if (sessionId == null || sessionId.isBlank()) {
            return error("sessionId 不能为空");
        }

        Long scenicId = null;
        if (scenicIdObj != null) {
            scenicId = scenicIdObj instanceof Number
                    ? ((Number) scenicIdObj).longValue()
                    : Long.parseLong(scenicIdObj.toString());
        }

        // 从 Redis 取对话元数据
        Map<String, Object> meta = chatMemoryService.getChatMetadata(sessionId);
        String intentType = meta != null ? (String) meta.get("intent") : null;
        Integer responseTimeMs = meta != null ? (Integer) meta.get("responseTimeMs") : null;
        Integer tokensUsed = meta != null ? (Integer) meta.get("tokensUsed") : null;
        String modelUsed = meta != null ? (String) meta.get("modelUsed") : null;

        chatMemoryService.syncToMySQL(sessionId, scenicId, visitorId,
                interactionType, intentType, responseTimeMs, tokensUsed, modelUsed);

        // 读取情感数据并更新最近一条交互记录
        Map<String, Object> emotionData = emotionDetectService.getEmotionResult(sessionId);
        if (emotionData != null) {
            chatMemoryService.updateLastInteractionEmotion(sessionId,
                    (String) emotionData.get("emotion"),
                    emotionData.get("confidence") instanceof Number
                            ? ((Number) emotionData.get("confidence")).doubleValue() : null);
            emotionDetectService.deleteEmotionResult(sessionId);
        }

        // 清除元数据 key
        chatMemoryService.deleteChatMetadata(sessionId);

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
        Path audioPath = Paths.get(System.getProperty("java.io.tmpdir"), "tts", filename);
        if (!Files.exists(audioPath)) {
            return ResponseEntity.notFound().build();
        }

        byte[] audioBytes = Files.readAllBytes(audioPath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(audioBytes);
    }

    /**
     * 获取会话列表（简要信息）
     *
     * @param visitorId 游客ID
     * @param scenicId 景区ID（可选）
     * @param page 页码
     * @param size 每页条数
     * @return 会话列表
     */
    @GetMapping("/conversation/list")
    public AjaxResult getConversationList(
            @RequestParam String visitorId,
            @RequestParam(required = false) Long scenicId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (visitorId == null || visitorId.isBlank()) {
            return error("visitorId 不能为空");
        }
        List<ConversationListVO> list = visitorConversationService.getConversationList(visitorId, scenicId, page, size);
        long total = visitorConversationService.getConversationCount(visitorId, scenicId);
        return success(Map.of("list", list, "total", total, "page", page, "size", size));
    }

    /**
     * 获取会话详情（完整对话）
     *
     * @param sessionId 会话ID
     * @return 会话详情
     */
    @GetMapping("/conversation/{sessionId}")
    public AjaxResult getConversationDetail(@PathVariable String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return error("sessionId 不能为空");
        }
        ConversationDetailVO detail = visitorConversationService.getConversationDetail(sessionId);
        if (detail == null) {
            return error("会话不存在");
        }
        return success(detail);
    }
}
