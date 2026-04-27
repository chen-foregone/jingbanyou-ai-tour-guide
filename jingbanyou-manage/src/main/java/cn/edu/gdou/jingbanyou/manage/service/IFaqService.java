package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.entity.Faq;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 常见问答 Service 接口
 *
 * @author jingbanyou
 */
public interface IFaqService extends IService<Faq>
{
    /**
     * 智能匹配相似问题（Redis 向量检索）
     *
     * @param scenicId 景区ID
     * @param question 用户问题
     * @return FAQ
     */
    public Faq matchSimilarQuestion(Long scenicId, String question);

    /**
     * 向量化 FAQ 并存入 Redis Vector Store
     *
     * @param faq FAQ 对象（需已持久化，id 不为空）
     */
    public void vectorizeFaq(Faq faq);

    /**
     * 统计 FAQ 被咨询次数
     *
     * @param id FAQ ID
     */
    public void incrementClickCount(Long id);

    /**
     * 点赞数 +1
     *
     * @param id FAQ ID
     */
    public void incrementHelpfulCount(Long id);

    /**
     * 踩数 +1
     *
     * @param id FAQ ID
     */
    public void incrementUnhelpfulCount(Long id);

    /**
     * 获取热门问答 TOP N
     *
     * @param scenicId 景区ID
     * @param limit 条数
     * @return FAQ列表
     */
    public List<Faq> getHotQuestions(Long scenicId, Integer limit);

    /**
     * 带分数的 FAQ 匹配（用于 RAG 预检）
     *
     * @param scenicId 景区ID
     * @param question 用户问题
     * @param threshold 相似度阈值（0~1）
     * @return 匹配结果，score 为 null 表示未命中
     */
    public FaqMatchResult matchWithScore(Long scenicId, String question, double threshold);

    /**
     * FAQ 匹配结果（含分数）
     */
    @lombok.Data
    public static class FaqMatchResult {
        private final Faq faq;
        private final Double score;

        public boolean isMatched() {
            return faq != null && score != null;
        }
    }

    /**
     * 删除 FAQ 并同步清理 Redis 向量
     *
     * @param id FAQ ID
     */
    void removeFaqWithVector(Long id);
}
