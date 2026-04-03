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
     * 智能匹配相似问题
     *
     * @param scenicId 景区ID
     * @param question 用户问题
     * @return FAQ
     */
    public Faq matchSimilarQuestion(Long scenicId, String question);

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
}
