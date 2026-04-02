package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.entity.Faq;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 常见问答 Service 接口（赛题要求：保障问答准确率 90%）
 */
public interface IFaqService extends IService<Faq> {

    /**
     * 智能匹配相似问题
     */
    Faq matchSimilarQuestion(Long scenicId, String question);

    /**
     * 统计 FAQ 被咨询次数
     */
    void incrementClickCount(Long id);

    /**
     * 获取热门问答 TOP10
     */
    java.util.List<Faq> getHotQuestions(Long scenicId, Integer limit);
}
