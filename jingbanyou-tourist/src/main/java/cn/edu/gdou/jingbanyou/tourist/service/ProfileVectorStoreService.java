package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.tourist.pojo.VisitorProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * 用户画像向量存储服务
 * 职责：
 * 1. 将游客偏好存入向量库（用于语义检索）
 * 2. 从向量库检索相似历史偏好
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileVectorStoreService {

    private final VectorStore profileVectorStore;

    private static final int DEFAULT_TOP_K = 3;

    /**
     * 将游客画像偏好存入向量库
     *
     * @param profile 游客画像
     */
    public void saveProfile(VisitorProfile profile) {
        if (profile.getVisitorId() == null || profile.getVisitorId().isBlank()) {
            log.debug("visitorId 为空，跳过向量存储");
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

            log.debug("画像已存入向量库，visitorId={}", profile.getVisitorId());
        } catch (Exception e) {
            log.warn("画像存入向量库失败，visitorId={}", profile.getVisitorId(), e);
        }
    }

    /**
     * 从向量库检索相似历史偏好
     *
     * @param visitorId 游客ID
     * @param query     语义查询（如"上次那个有花的地方"）
     * @return 匹配的历史偏好列表
     */
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

            log.debug("从向量库检索到 {} 条相似偏好，visitorId={}, query={}",
                    results.size(), visitorId, query);
            return results;

        } catch (Exception e) {
            log.warn("从向量库检索失败，visitorId={}", visitorId, e);
            return List.of();
        }
    }

    /**
     * 根据 visitorId 删除向量库中的记录
     */
    public void deleteProfile(String visitorId) {
        if (visitorId == null || visitorId.isBlank()) {
            return;
        }
        try {
            profileVectorStore.delete(List.of(visitorId));
            log.debug("已从向量库删除画像，visitorId={}", visitorId);
        } catch (Exception e) {
            log.warn("从向量库删除画像失败，visitorId={}", visitorId, e);
        }
    }

    /**
     * 构建画像文本描述（用于向量化）
     */
    private String buildProfileText(VisitorProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("游客画像：");

        if (profile.getGroupType() != null) {
            sb.append("出行类型").append(profile.getGroupType());
        }

        if (profile.getPreferRouteType() != null) {
            sb.append("，偏好路线类型").append(profile.getPreferRouteType());
        }

        if (profile.getInterestTags() != null && !profile.getInterestTags().isEmpty()) {
            sb.append("，兴趣标签").append(String.join("、", profile.getInterestTags()));
        }

        if (profile.getVisitedSpots() != null && !profile.getVisitedSpots().isEmpty()) {
            sb.append("，已游览景点").append(String.join("、", profile.getVisitedSpots()));
        }

        return sb.toString();
    }

    /**
     * 构建元数据
     */
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
     * 相似画像结果
     */
    @lombok.Data
    @lombok.Builder
    public static class SimilarProfile {
        private String visitorId;
        private String content;
        private Double score;
        private Map<String, Object> metadata;
    }
}
