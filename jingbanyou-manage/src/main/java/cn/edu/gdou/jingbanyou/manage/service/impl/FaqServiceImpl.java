package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.Faq;
import cn.edu.gdou.jingbanyou.manage.mapper.FaqMapper;
import cn.edu.gdou.jingbanyou.manage.service.IFaqService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 常见问答 Service 实现类（赛题要求：保障问答准确率 90%）
 * 使用 DashScope text-embedding-v2 + Redis Stack 向量检索
 *
 * @author jingbanyou
 */
@Slf4j
@Service
public class FaqServiceImpl extends ServiceImpl<FaqMapper, Faq> implements IFaqService {

    @Autowired
    @Qualifier("faqVectorStore")
    private VectorStore redisVectorStore;

    /**
     * 智能匹配相似问题（Redis 向量检索）
     *
     * @param scenicId 景区ID
     * @param question 用户问题
     * @return 匹配到的 FAQ，未命中返回 null
     */
    @Override
    public Faq matchSimilarQuestion(Long scenicId, String question) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(1)
                .similarityThreshold(0.75)
                .filterExpression("scenicId == '" + scenicId + "'")
                .build();

        List<Document> results = redisVectorStore.similaritySearch(request);
        if (results == null || results.isEmpty())
        {
            return null;
        }

        String faqIdStr = (String) results.get(0).getMetadata().get("faqId");
        if (faqIdStr == null)
        {
            return null;
        }

        return getById(Long.parseLong(faqIdStr));
    }

    /**
     * 向量化 FAQ 并存入 Redis Vector Store
     *
     * @param faq FAQ 对象（需已持久化，id 不为空）
     */
    @Override
    public void vectorizeFaq(Faq faq) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("faqId", String.valueOf(faq.getId()));
        metadata.put("scenicId", String.valueOf(faq.getScenicId()));

        Document doc = new Document(faq.getQuestion(), metadata);
        redisVectorStore.add(List.of(doc));

        faq.setVectorId(doc.getId());
        updateById(faq);
        log.info("FAQ [{}] 向量化完成，vectorId={}", faq.getId(), doc.getId());
    }

    /**
     * FAQ 被咨询次数 +1
     *
     * @param id FAQ ID
     */
    @Override
    public void incrementClickCount(Long id) {
        baseMapper.incrementClickCount(id);
    }

    /**
     * FAQ 点赞数 +1
     *
     * @param id FAQ ID
     */
    @Override
    public void incrementHelpfulCount(Long id) {
        baseMapper.incrementHelpfulCount(id);
    }

    /**
     * FAQ 踩数 +1
     *
     * @param id FAQ ID
     */
    @Override
    public void incrementUnhelpfulCount(Long id) {
        baseMapper.incrementUnhelpfulCount(id);
    }

    /**
     * 获取热门问答 TOP N
     *
     * @param scenicId 景区ID
     * @param limit 条数
     * @return FAQ列表
     */
    @Override
    public List<Faq> getHotQuestions(Long scenicId, Integer limit) {
        Page<Faq> page = new Page<>(1, limit != null ? limit : 10);
        Page<Faq> result = page(page, new LambdaQueryWrapper<Faq>()
                .eq(Faq::getScenicId, scenicId)
                .eq(Faq::getStatus, 1)
                .orderByDesc(Faq::getClickCount));
        return result.getRecords();
    }

    /**
     * 带分数的 FAQ 匹配（用于 RAG 预检）
     *
     * @param scenicId 景区ID
     * @param question 用户问题
     * @param threshold 相似度阈值（0~1）
     * @return 匹配结果，score 为 null 表示未命中
     */
    @Override
    public FaqMatchResult matchWithScore(Long scenicId, String question, double threshold) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(1)
                .similarityThreshold(threshold)
                .filterExpression("scenicId == '" + scenicId + "'")
                .build();

        List<Document> results = redisVectorStore.similaritySearch(request);
        if (results == null || results.isEmpty()) {
            return new FaqMatchResult(null, null);
        }

        Document doc = results.get(0);
        Double score = doc.getScore();

        // 显式校验分数，避免 VectorStore 实现差异导致阈值失效
        if (score == null || score < threshold) {
            return new FaqMatchResult(null, null);
        }

        String faqIdStr = (String) doc.getMetadata().get("faqId");
        if (faqIdStr == null) {
            return new FaqMatchResult(null, null);
        }

        Faq faq = getById(Long.parseLong(faqIdStr));
        if (faq == null) {
            return new FaqMatchResult(null, null);
        }

        return new FaqMatchResult(faq, score);
    }
}
