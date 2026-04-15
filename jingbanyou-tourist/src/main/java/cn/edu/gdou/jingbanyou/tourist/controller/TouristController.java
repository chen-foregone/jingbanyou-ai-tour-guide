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
import cn.hutool.core.bean.BeanUtil;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private final GraphConfiguration graphConfiguration;
    private final TranscribeService transcribeService;
    private final ChatMemoryService chatMemoryService;

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
     * 文本对话
     * @param request 包含 message、sessionId、scenicId
     * @return AI回复、意图、语音
     */
    @PostMapping("/chat")
    public AjaxResult chat(@RequestBody Map<String, Object> request) {
        String message = (String) request.get("message");
        // 打印字节十六进制
        StringBuilder hex = new StringBuilder();
        for (byte b : message.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            hex.append(String.format("%02X ", b));
        }
        log.info("[Chat入口] message={}, UTF-8 bytes: {}", message, hex);
        String sessionId = (String) request.get("sessionId");
        Long scenicId = request.get("scenicId") != null
                ? ((Number) request.get("scenicId")).longValue() : null;
        log.info("[Chat入口] scenicId={}, requestBody={}", scenicId, request);

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

            String answer = result.value(GraphStateKey.ANSWER, String.class).orElse("");
            String intent = (String) result.value(GraphStateKey.INTENT).orElse("");

            String replyId = "assistant-" + System.currentTimeMillis();
            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("id", replyId);
            reply.put("role", "assistant");
            reply.put("content", answer);

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

            Map<String, Object> voice = Map.of("audioUrl", "");

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
}
