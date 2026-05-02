package cn.edu.gdou.jingbanyou.tourist.service;

import java.util.Map;

/**
 * 情感检测服务接口
 *
 * @author jingbanyou
 */
public interface IEmotionDetectService {

    /**
     * 检测情感（异步调用）
     * 携带历史上下文，提供更准确的情感判断
     *
     * @param sessionId 会话ID
     * @param currentMessage 当前用户消息
     * @param chatHistory 历史对话（可选）
     */
    void detectEmotionAsync(String sessionId, String currentMessage, String chatHistory);

    /**
     * 从 Redis 获取情感结果
     *
     * @param sessionId 会话ID
     * @return 情感数据（emotion, confidence）
     */
    Map<String, Object> getEmotionResult(String sessionId);

    /**
     * 删除 Redis 中的情感结果
     *
     * @param sessionId 会话ID
     */
    void deleteEmotionResult(String sessionId);
}
