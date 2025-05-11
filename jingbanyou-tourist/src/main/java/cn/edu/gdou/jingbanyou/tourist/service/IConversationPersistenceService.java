package cn.edu.gdou.jingbanyou.tourist.service;

/**
 * 对话持久化服务接口
 * 负责对话结束时将 Redis 中的数据同步到 MySQL
 *
 * @author jingbanyou
 */
public interface IConversationPersistenceService {

    /**
     * 对话结束时，异步全量同步到 MySQL
     *
     * @param sessionId       会话 ID
     * @param scenicId        景区 ID
     * @param humanId         数字人 ID
     * @param visitorId       游客 ID
     * @param interactionType 交互类型（text/voice）
     * @param intentType      意图类型
     * @param responseTimeMs  响应耗时（毫秒）
     * @param tokensUsed      消耗 Token 数量
     * @param modelUsed       使用的 AI 模型
     * @param ragDocs         引用的知识文档（JSON 数组字符串）
     */
    void syncToMySQL(String sessionId, Long scenicId, Long humanId, String visitorId,
                     String interactionType, String intentType,
                     Integer responseTimeMs, Integer tokensUsed, String modelUsed,
                     String ragDocs);

    /**
     * 更新最近一条交互记录的情感数据
     *
     * @param sessionId          会话 ID
     * @param emotionDetected    情感类型（positive/neutral/negative）
     * @param emotionConfidence  情感置信度
     */
    void updateLastInteractionEmotion(String sessionId, String emotionDetected, Double emotionConfidence);
}
