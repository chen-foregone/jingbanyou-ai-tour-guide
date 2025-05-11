package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.tourist.pojo.VisitorProfile;
import java.util.List;
import java.util.Map;

/**
 * 用户画像向量存储服务接口
 *
 * @author jingbanyou
 */
public interface IProfileVectorStoreService {

    /**
     * 将游客画像偏好存入向量库
     *
     * @param profile 游客画像
     */
    void saveProfile(VisitorProfile profile);

    /**
     * 从向量库检索相似历史偏好
     *
     * @param visitorId 游客ID
     * @param query 语义查询
     * @return 匹配的历史偏好列表
     */
    List<SimilarProfile> retrieveSimilarProfiles(String visitorId, String query);

    /**
     * 根据 visitorId 删除向量库中的记录
     *
     * @param visitorId 游客ID
     */
    void deleteProfile(String visitorId);

    /**
     * 根据 visitorId 精确读取向量库中的画像记录
     * <p>用于 Redis TTL 过期后的画像恢复，无需向量检索，直接通过 ID 命中
     *
     * @param visitorId 游客ID
     * @return 匹配记录列表（最多1条），无则返回空列表
     */
    List<SimilarProfile> retrieveByVisitorId(String visitorId);

    /**
     * 画像检索结果
     */
    @lombok.Data
    @lombok.Builder
    class SimilarProfile {
        private String visitorId;
        private String content;
        private Double score;
        private Map<String, Object> metadata;
    }
}
