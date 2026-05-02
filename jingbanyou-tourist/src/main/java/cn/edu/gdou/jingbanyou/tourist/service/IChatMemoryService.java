package cn.edu.gdou.jingbanyou.tourist.service;

import java.util.Map;

/**
 * 对话记忆服务接口
 *
 * @author jingbanyou
 */
public interface IChatMemoryService {

    /**
     * 对话结束时，异步全量同步到 MySQL
     *
     * @param sessionId       会话ID
     * @param scenicId        景区ID
     * @param visitorId       游客ID
     * @param interactionType 交互类型（如 text、voice）
     */
    void syncToMySQL(String sessionId, Long scenicId, String visitorId, String interactionType);

    /**
     * 便利方法，默认 text 类型
     *
     * @param sessionId 会话ID
     * @param scenicId  景区ID
     * @param visitorId 游客ID
     */
    default void syncToMySQL(String sessionId, Long scenicId, String visitorId) {
        syncToMySQL(sessionId, scenicId, visitorId, "text", null, null, null, null);
    }

    /**
     * 对话结束时，同步到 MySQL 并携带完整元数据
     *
     * @param sessionId       会话ID
     * @param scenicId        景区ID
     * @param visitorId       游客ID
     * @param interactionType 交互类型（text/voice）
     * @param intentType      意图类型
     * @param responseTimeMs  响应耗时（毫秒）
     * @param tokensUsed      消耗 Token 数量
     * @param modelUsed       使用的 AI 模型
     */
    void syncToMySQL(String sessionId, Long scenicId, String visitorId,
                     String interactionType, String intentType,
                     Integer responseTimeMs, Integer tokensUsed, String modelUsed);

    /**
     * 记录单轮对话（不含会话结束标志）
     * 在 chat()/chatStream() 成功后调用，写入当前这一轮交互
     *
     * @param sessionId       会话ID
     * @param scenicId        景区ID
     * @param visitorId       游客ID
     * @param userQuestion    游客问题
     * @param aiAnswer        AI 回答
     * @param interactionType 交互类型
     * @param intentType      意图类型
     * @param responseTimeMs  响应耗时（毫秒）
     * @param tokensUsed      消耗 Token 数量
     * @param modelUsed       使用的 AI 模型
     */
    void recordSingleTurn(String sessionId, Long scenicId, Long humanId, String visitorId,
                          String userQuestion, String aiAnswer,
                          String interactionType, String intentType,
                          Integer responseTimeMs, Integer tokensUsed, String modelUsed);

    /**
     * 更新最近一条交互记录的情感数据
     * 在 endChat 时调用，补充异步检测到的情感信息
     *
     * @param sessionId 会话ID
     * @param emotionDetected  情感类型（positive/neutral/negative）
     * @param emotionConfidence 情感置信度
     */
    void updateLastInteractionEmotion(String sessionId, String emotionDetected, Double emotionConfidence);

    /**
     * 保存对话元数据到 Redis
     *
     * @param sessionId     会话ID
     * @param intent        意图类型
     * @param tokensUsed    消耗 Token 数量
     * @param modelUsed     使用的 AI 模型
     * @param responseTimeMs 响应耗时（毫秒）
     */
    void saveChatMetadata(String sessionId, String intent, Integer tokensUsed,
                          String modelUsed, int responseTimeMs);

    /**
     * 从 Redis 获取对话元数据
     *
     * @param sessionId 会话ID
     * @return 元数据 Map
     */
    Map<String, Object> getChatMetadata(String sessionId);

    /**
     * 从 Redis 删除对话元数据
     *
     * @param sessionId 会话ID
     */
    void deleteChatMetadata(String sessionId);
}
