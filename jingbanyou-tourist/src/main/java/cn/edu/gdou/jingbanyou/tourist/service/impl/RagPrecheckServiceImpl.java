package cn.edu.gdou.jingbanyou.tourist.service.impl;

import cn.edu.gdou.jingbanyou.manage.service.IFaqService;
import cn.edu.gdou.jingbanyou.tourist.service.IRagPrecheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * RAG 预检服务实现
 *
 * 在调用 AI 之前，先查询 FAQ 向量库：
 * - 相似度 >= 阈值 → 直接返回 FAQ 答案，跳过 AI 调用
 * - 相似度 < 阈值 → 返回空 Optional，继续后续 AI 流程
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@ConfigurationProperties(prefix = "jingbanyou.ai.rag-precheck")
public class RagPrecheckServiceImpl implements IRagPrecheckService {

    /**
     * FAQ 相似度阈值，默认 0.75
     */
    private double threshold = 0.75;

    private final IFaqService faqService;

    @Autowired
    public RagPrecheckServiceImpl(IFaqService faqService) {
        this.faqService = faqService;
    }

    @Override
    public Optional<String> fastMatch(String question, Long scenicId) {
        if (scenicId == null || question == null || question.isBlank()) {
            return Optional.empty();
        }

        try {
            IFaqService.FaqMatchResult result = faqService.matchWithScore(scenicId, question, threshold);

            if (!result.isMatched()) {
                log.debug("[RAG预检] 未命中，scenicId={}, question={}", scenicId, question);
                return Optional.empty();
            }

            log.info("[RAG预检] 命中 FAQ，scenicId={}, question={}, score={}, matchedQuestion={}",
                    scenicId, question, result.getScore(), result.getFaq().getQuestion());

            // 点击量 +1
            faqService.incrementClickCount(result.getFaq().getId());

            return Optional.ofNullable(result.getFaq().getAnswer());

        } catch (Exception e) {
            log.warn("[RAG预检] 查询异常，scenicId={}, question={}", scenicId, question, e);
            return Optional.empty();
        }
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }
}
