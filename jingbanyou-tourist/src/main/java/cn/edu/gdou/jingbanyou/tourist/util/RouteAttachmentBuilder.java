package cn.edu.gdou.jingbanyou.tourist.util;

import cn.edu.gdou.jingbanyou.common.utils.JsonEscapeUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路线附件构建工具类
 * 统一处理路线附件 JSON 和路线摘要的构建
 *
 * @author jingbanyou
 */
@Component
@RequiredArgsConstructor
public class RouteAttachmentBuilder {

    private final ObjectMapper objectMapper;

    /**
     * 构建路线附件 JSON 数组
     *
     * @param rawRoutes 原始路线列表
     * @return JSON 字符串
     */
    public String buildAttachmentsJson(List<?> rawRoutes) {
        if (rawRoutes == null || rawRoutes.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> attachments = new ArrayList<>();
        for (int i = 0; i < rawRoutes.size(); i++) {
            Map<String, Object> route = (Map<String, Object>) rawRoutes.get(i);
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("id", "route-" + i);
            attachment.put("title", route.getOrDefault("description", route.getOrDefault("title", "推荐路线")));
            attachment.put("summary", buildRouteSummary(route));
            attachment.put("duration", route.getOrDefault("duration", ""));
            attachments.add(attachment);
        }
        return JsonEscapeUtil.escape(attachments);
    }

    /**
     * 构建路线摘要字符串
     *
     * @param route 路线数据
     * @return 摘要字符串
     */
    public String buildRouteSummary(Map<String, Object> route) {
        StringBuilder sb = new StringBuilder();
        if (route.get("suitableFor") != null) {
            sb.append("适合：").append(route.get("suitableFor"));
        }
        if (route.get("tips") != null) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append("提示：").append(route.get("tips"));
        }
        return sb.toString();
    }
}
