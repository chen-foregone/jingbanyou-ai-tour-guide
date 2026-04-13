package cn.edu.gdou.jingbanyou.tourist.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Anonymous;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.manage.dto.ScenicAreaVO;
import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.manage.service.IDigitalHumanConfigService;
import cn.edu.gdou.jingbanyou.manage.service.IScenicAreaService;
import cn.edu.gdou.jingbanyou.tourist.constant.GraphStateKey;
import cn.edu.gdou.jingbanyou.tourist.graph.GraphConfiguration;
import cn.edu.gdou.jingbanyou.tourist.service.ChatMemoryService;
import cn.edu.gdou.jingbanyou.tourist.service.TranscribeService;
import cn.edu.gdou.jingbanyou.tourist.service.TtsService;
import cn.hutool.core.bean.BeanUtil;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 前台游客端 TouristController
 * <p>
 * 路径: /api/tourist/*
 * 前端 tourist-web 专用接口，公开无需认证
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
    private final GraphConfiguration graphConfiguration;
    private final TtsService ttsService;
    private final TranscribeService transcribeService;
    private final ChatMemoryService chatMemoryService;

    /**
     * GET /api/tourist/bootstrap
     * 前台首屏初始化数据
     */
    @GetMapping("/bootstrap")
    public AjaxResult bootstrap(@RequestParam Long scenicId) {
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
     * POST /api/tourist/chat
     * 文本对话，复用 LangGraph 执行逻辑
     */
    @PostMapping("/chat")
    public AjaxResult chat(@RequestBody Map<String, Object> request) {
        String message = (String) request.get("message");
        String sessionId = (String) request.get("sessionId");
        Long scenicId = request.get("scenicId") != null
                ? ((Number) request.get("scenicId")).longValue() : null;

        if (message == null || message.isBlank()) {
            return error("消息内容不能为空");
        }
        if (sessionId == null || sessionId.isBlank()) {
            return error("sessionId 不能为空");
        }

        try {
            Map<String, Object> initialState = new HashMap<>();
            initialState.put(GraphStateKey.SESSION_ID, sessionId);
            initialState.put(GraphStateKey.QUESTION, message);
            initialState.put(GraphStateKey.HISTORY, "");
            initialState.put(GraphStateKey.LANGUAGE, "zh");
            if (scenicId != null) {
                initialState.put(GraphStateKey.SCENIC_ID, scenicId);
            }

            CompiledGraph graph = graphConfiguration.compiledGraph();
            OverAllState result = graph.invoke(initialState)
                    .orElseThrow(() -> new RuntimeException("Graph 执行返回空结果"));

            String answer = (String) result.value(GraphStateKey.ANSWER).orElse("");
            String intent = (String) result.value(GraphStateKey.INTENT).orElse("");

            // 构造回复结构
            String replyId = "assistant-" + System.currentTimeMillis();
            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("id", replyId);
            reply.put("role", "assistant");
            reply.put("content", answer);

            // 路线规划意图时，polishedRoutes → attachments
            if (GraphConfiguration.INTENT_ROUTE_PLAN.equals(intent)) {
                List<?> rawRoutes = result.value(GraphStateKey.POLISHED_ROUTES, List.class).orElse(null);
                if (rawRoutes != null && !rawRoutes.isEmpty()) {
                    reply.put("attachments", buildRouteAttachments(rawRoutes));
                } else {
                    reply.put("attachments", List.of());
                }
            } else {
                reply.put("attachments", List.of());
            }

            // voice 部分 TTS 合成
            DigitalHumanConfig dh = scenicId != null
                    ? digitalHumanConfigService.getDefaultByScenicId(scenicId) : null;
            String audioUrl = ttsService.synthesize(answer, sessionId, dh);
            Map<String, Object> voice = Map.of("audioUrl", audioUrl);

            return success(Map.of(
                    "reply", reply,
                    "intent", intent,
                    "voice", voice
            ));

        } catch (Exception e) {
            log.error("Graph 执行失败 sessionId={}", sessionId, e);
            return error("处理失败: " + e.getMessage());
        }
    }

    /**
     * 将 Graph 的 polishedRoutes 转换为前端附件格式
     * <p>
     * Graph 返回: List<Map> — 每条路线含 description/suitableFor/tips/duration 等
     * 前端期望: RouteAttachment 格式
     */
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
            item.put("summary", buildRouteSummary(route));
            item.put("duration", route.getOrDefault("duration", ""));
            item.put("tags", buildRouteTags(route));
            items.add(item);
        }

        routeAttachment.put("title", "为您推荐以下游览路线");
        routeAttachment.put("meta", items.size() + " 条路线");
        routeAttachment.put("items", items);
        attachments.add(routeAttachment);

        return attachments;
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
        return sb.length() > 0 ? sb.toString() : "";
    }

    private List<String> buildRouteTags(Map<String, Object> route) {
        List<String> tags = new ArrayList<>();
        if (route.get("suitableFor") != null) {
            tags.add(route.get("suitableFor").toString());
        }
        if (route.get("strategy") != null) {
            tags.add(route.get("strategy").toString());
        }
        return tags;
    }

    /**
     * POST /api/tourist/tts
     * 独立 TTS 接口，将文本合成为语音并上传 OSS
     *
     * @param text     要合成的文本
     * @param scenicId 景区 ID（用于获取数字人音色配置）
     * @param sessionId 会话 ID
     */
    @PostMapping("/tts")
    public AjaxResult tts(@RequestBody Map<String, Object> request) {
        String text = (String) request.get("text");
        Object scenicIdObj = request.get("scenicId");
        String sessionId = (String) request.get("sessionId");

        if (text == null || text.isBlank()) {
            return error("文本内容不能为空");
        }
        if (sessionId == null || sessionId.isBlank()) {
            return error("sessionId 不能为空");
        }

        DigitalHumanConfig digitalHuman = null;
        if (scenicIdObj != null) {
            Long scenicId = scenicIdObj instanceof Number
                    ? ((Number) scenicIdObj).longValue()
                    : Long.parseLong(scenicIdObj.toString());
            digitalHuman = digitalHumanConfigService.getDefaultByScenicId(scenicId);
        }

        String audioUrl = ttsService.synthesize(text, sessionId, digitalHuman);

        if (audioUrl == null || audioUrl.isEmpty()) {
            return error("语音合成失败");
        }

        return success(Map.of("audioUrl", audioUrl));
    }

    /**
     * POST /api/tourist/voice/transcribe
     * 语音转文字（ASR），使用 DashScope Paraformer 模型
     *
     * @param file     音频文件（支持 mp3/wav/m4a/aac/pcm）
     * @param language 语言提示，默认为 zh
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
     * POST /api/tourist/chat/end
     * 前端页面离开时调用，触发对话历史异步持久化到 MySQL
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
}
