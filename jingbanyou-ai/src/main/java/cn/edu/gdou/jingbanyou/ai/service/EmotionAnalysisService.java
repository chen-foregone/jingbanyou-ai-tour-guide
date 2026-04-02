package cn.edu.gdou.jingbanyou.ai.service;

import java.util.Map;

/**
 * 情感分析 Service（AI 中台核心能力）
 * 
 * 功能：分析游客情感，生成情感报告
 */
public interface EmotionAnalysisService {

    /**
     * 分析文本情感
     */
    Map<String, Object> analyzeTextEmotion(String text);

    /**
     * 批量分析交互记录
     */
    Map<String, Object> batchAnalyze(Long scenicId, String startDate, String endDate);

    /**
     * 获取情感分布
     */
    Map<String, Double> getEmotionDistribution(Long scenicId);
}
