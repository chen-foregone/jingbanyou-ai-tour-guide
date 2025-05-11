package cn.edu.gdou.jingbanyou.tourist.service.impl;

import cn.edu.gdou.jingbanyou.tourist.pojo.VisitorProfile;
import cn.edu.gdou.jingbanyou.tourist.service.IProfileVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;

import java.time.Instant;
import java.util.*;

/**
 * 用户画像向量存储服务
 *
 * <p>职责：
 * <p>  1. 保存画像到向量库（用于 Redis TTL 过期后的长期恢复）
 * <p>  2. 根据 visitorId 精确读取画像（直接查 Redis Key，无向量检索开销）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileVectorStoreService implements IProfileVectorStoreService {

    private static final String PREFIX = "profile:";

    /** 向量检索（不被当前逻辑使用，保留接口契约） */
    private final VectorStore profileVectorStore;

    /** 直接操作 Redis，精确读取向量库中的画像 JSON */
    private final @Qualifier("jedisPooled") JedisPooled jedisPooled;

    private static final int DEFAULT_TOP_K = 3;

    public void saveProfile(VisitorProfile profile) {
        if (profile.getVisitorId() == null || profile.getVisitorId().isBlank()) {
            log.debug("[向量库] visitorId 为空，跳过存储");
            return;
        }

        try {
            String text = buildProfileText(profile);
            Map<String, Object> metadata = buildMetadata(profile);

            profileVectorStore.add(List.of(
                    org.springframework.ai.document.Document.builder()
                            .id(profile.getVisitorId())
                            .text(text)
                            .metadata(metadata)
                            .build()
            ));

            log.debug("[向量库] 画像已存储，visitorId={}", profile.getVisitorId());
        } catch (Exception e) {
            log.warn("[向量库] 存储失败，visitorId={}", profile.getVisitorId(), e);
        }
    }

    public List<SimilarProfile> retrieveSimilarProfiles(String visitorId, String query) {
        if (visitorId == null || visitorId.isBlank() || query == null || query.isBlank()) {
            return List.of();
        }

        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(DEFAULT_TOP_K)
                    .filterExpression("visitorId == '" + visitorId + "'")
                    .build();

            List<org.springframework.ai.document.Document> docs =
                    profileVectorStore.similaritySearch(request);

            List<SimilarProfile> results = new ArrayList<>();
            for (org.springframework.ai.document.Document doc : docs) {
                SimilarProfile sp = SimilarProfile.builder()
                        .visitorId(doc.getId())
                        .content(doc.getText())
                        .score(doc.getScore() != null ? doc.getScore() : 0.0)
                        .metadata(doc.getMetadata())
                        .build();
                results.add(sp);
            }

            log.debug("[向量库] 检索到 {} 条相似画像，visitorId={}", results.size(), visitorId);
            return results;

        } catch (Exception e) {
            log.warn("[向量库] 检索失败，visitorId={}", visitorId, e);
            return List.of();
        }
    }

    /**
     * 根据 visitorId 精确读取向量库中的画像
     * <p>直接通过 Redis Key 读取 JSON，不走向量检索
     */
    public List<SimilarProfile> retrieveByVisitorId(String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            return List.of();
        }

        try {
            String key = PREFIX + visitorId;
            String json = jedisPooled.get(key);

            if (json == null || json.isBlank()) {
                log.debug("[向量库] 未找到画像，visitorId={}", visitorId);
                return List.of();
            }

            // RedisVectorStore 存储格式：包含 $ 字段的 JSON
            // 提取 metadata 部分
            Map<String, Object> metadata = parseMetadataFromRedisJson(json);
            if (metadata == null) {
                return List.of();
            }

            String content = (String) metadata.getOrDefault("content", buildFallbackText(metadata));

            SimilarProfile sp = SimilarProfile.builder()
                    .visitorId(visitorId)
                    .content(content)
                    .score(1.0)  // 精确匹配，置信度 1.0
                    .metadata(metadata)
                    .build();

            log.debug("[向量库] 精确读取成功，visitorId={}", visitorId);
            return List.of(sp);

        } catch (Exception e) {
            log.warn("[向量库] 精确读取失败，visitorId={}: {}", visitorId, e.getMessage());
            return List.of();
        }
    }

    public void deleteProfile(String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            return;
        }
        try {
            profileVectorStore.delete(List.of(visitorId));
            log.debug("[向量库] 已删除画像，visitorId={}", visitorId);
        } catch (Exception e) {
            log.warn("[向量库] 删除失败，visitorId={}", visitorId, e);
        }
    }

    private String buildProfileText(VisitorProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("游客画像：");

        if (profile.getGroupType() != null) {
            sb.append("出行类型").append(profile.getGroupType()).append("，");
        }
        if (profile.getPreferRouteType() != null) {
            sb.append("偏好路线类型").append(profile.getPreferRouteType()).append("，");
        }
        if (profile.getInterestTags() != null && !profile.getInterestTags().isEmpty()) {
            sb.append("兴趣标签").append(String.join("、", profile.getInterestTags())).append("，");
        }
        if (profile.getVisitedSpots() != null && !profile.getVisitedSpots().isEmpty()) {
            sb.append("已游览景点").append(String.join("、", profile.getVisitedSpots()));
        }

        return sb.toString();
    }

    private Map<String, Object> buildMetadata(VisitorProfile profile) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("visitorId", profile.getVisitorId());
        metadata.put("groupType", profile.getGroupType() != null ? profile.getGroupType() : "");
        metadata.put("preferRouteType", profile.getPreferRouteType() != null ? profile.getPreferRouteType() : "");
        metadata.put("interestTags", profile.getInterestTags() != null ?
                String.join(",", profile.getInterestTags()) : "");
        metadata.put("visitedSpots", profile.getVisitedSpots() != null ?
                String.join(",", profile.getVisitedSpots()) : "");
        metadata.put("timestamp", Instant.now().toEpochMilli());
        return metadata;
    }

    /**
     * 解析 RedisVectorStore 的 JSON 存储格式，提取 metadata
     * <p>RedisVectorStore 存储格式：
     * <p>{ "$": { ... }, "content": "...", "metadata": { ... }, "embedding": [...] }
     */
    private Map<String, Object> parseMetadataFromRedisJson(String json) {
        try {
            // 简单的字符串提取，避开完整 JSON 解析依赖
            // 格式: { ..., "metadata": {...}, ... }
            int metaStart = json.indexOf("\"metadata\"");
            if (metaStart < 0) {
                return null;
            }

            // 找到 metadata 对象的开始和结束
            int braceStart = json.indexOf("{", metaStart);
            int braceEnd = json.indexOf("}", metaStart);
            int nextBrace = json.indexOf("{", metaStart + 1);

            // 处理嵌套
            while (nextBrace > 0 && nextBrace < braceEnd) {
                // 找配对的 }
                int count = 1;
                int i = braceStart + 1;
                while (count > 0 && i < json.length()) {
                    if (json.charAt(i) == '{') count++;
                    else if (json.charAt(i) == '}') count--;
                    i++;
                }
                braceEnd = i - 1;
                nextBrace = json.indexOf("{", i);
            }

            String metaJson = json.substring(braceStart, braceEnd + 1);

            // 手动解析字段
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("visitorId", extractStringValue(metaJson, "visitorId"));
            metadata.put("groupType", extractStringValue(metaJson, "groupType"));
            metadata.put("preferRouteType", extractStringValue(metaJson, "preferRouteType"));
            metadata.put("interestTags", extractStringValue(metaJson, "interestTags"));
            metadata.put("visitedSpots", extractStringValue(metaJson, "visitedSpots"));

            return metadata;

        } catch (Exception e) {
            log.warn("[向量库] JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractStringValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) {
            return "";
        }
        int colon = json.indexOf(":", idx);
        if (colon < 0) {
            return "";
        }
        int start = colon + 1;
        // 跳过空白
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) {
            if (json.charAt(start) == '\"') {
                start++;
                break;
            }
            start++;
        }
        int end = start;
        while (end < json.length() && json.charAt(end) != '\"' && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        String val = json.substring(start, end).trim();
        return "null".equals(val) ? "" : val;
    }

    private String buildFallbackText(Map<String, Object> metadata) {
        StringBuilder sb = new StringBuilder("游客画像：");
        String groupType = (String) metadata.getOrDefault("groupType", "");
        String preferRouteType = (String) metadata.getOrDefault("preferRouteType", "");
        String interestTags = (String) metadata.getOrDefault("interestTags", "");
        String visitedSpots = (String) metadata.getOrDefault("visitedSpots", "");
        if (!groupType.isEmpty()) sb.append("出行类型").append(groupType).append("，");
        if (!preferRouteType.isEmpty()) sb.append("偏好路线类型").append(preferRouteType).append("，");
        if (!interestTags.isEmpty()) sb.append("兴趣标签").append(interestTags).append("，");
        if (!visitedSpots.isEmpty()) sb.append("已游览景点").append(visitedSpots);
        return sb.toString();
    }
}
