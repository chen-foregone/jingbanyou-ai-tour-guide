package cn.edu.gdou.jingbanyou.tourist.service;

import java.util.Optional;

/**
 * RAG 预检服务
 *
 * 职责：在调用 AI 之前，先查询 FAQ 向量库
 * - 相似度 >= 阈值 → 直接返回 FAQ 答案，跳过 AI 调用
 * - 相似度 < 阈值 → 返回空 Optional，继续后续 AI 流程
 */
public interface IRagPrecheckService {

    /**
     * 快速匹配 FAQ 答案
     *
     * @param question 用户问题
     * @param scenicId 景区 ID
     * @return 命中的 FAQ 答案，未命中返回空
     */
    Optional<String> fastMatch(String question, Long scenicId);
}
