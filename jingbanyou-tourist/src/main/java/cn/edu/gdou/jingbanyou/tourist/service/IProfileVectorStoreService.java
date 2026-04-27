package cn.edu.gdou.jingbanyou.tourist.service;

import cn.edu.gdou.jingbanyou.tourist.pojo.VisitorProfile;
import cn.edu.gdou.jingbanyou.tourist.service.ProfileVectorStoreService.SimilarProfile;
import java.util.List;

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
}
