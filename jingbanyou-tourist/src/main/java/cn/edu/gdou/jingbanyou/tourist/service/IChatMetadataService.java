package cn.edu.gdou.jingbanyou.tourist.service;

import java.util.Map;

/**
 * 对话元数据服务接口
 * 负责对话元数据的 Redis 读写操作
 *
 * @author jingbanyou
 */
public interface IChatMetadataService {

    /**
     * 保存对话元数据到 Redis
     *
     * @param sessionId      会话 ID
     * @param intent         意图类型
     * @param tokensUsed     消耗 Token 数量
     * @param modelUsed      使用的 AI 模型
     * @param responseTimeMs 响应耗时（毫秒）
     * @param ragDocs        引用的知识文档（JSON 数组字符串）
     */
    void saveMetadata(String sessionId, String intent, Integer tokensUsed,
                      String modelUsed, int responseTimeMs, String ragDocs);

    /**
     * 从 Redis 获取对话元数据
     *
     * @param sessionId 会话 ID
     * @return 元数据 Map，无数据返回 null
     */
    Map<String, Object> getMetadata(String sessionId);

    /**
     * 从 Redis 删除对话元数据
     *
     * @param sessionId 会话 ID
     */
    void deleteMetadata(String sessionId);
}
