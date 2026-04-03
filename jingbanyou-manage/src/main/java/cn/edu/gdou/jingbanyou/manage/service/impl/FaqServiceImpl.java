package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.Faq;
import cn.edu.gdou.jingbanyou.manage.mapper.FaqMapper;
import cn.edu.gdou.jingbanyou.manage.service.IFaqService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 常见问答 Service 实现类（赛题要求：保障问答准确率 90%）
 *
 * @author jingbanyou
 */
@Slf4j
@Service
public class FaqServiceImpl extends ServiceImpl<FaqMapper, Faq> implements IFaqService
{
    @Override
    public Faq matchSimilarQuestion(Long scenicId, String question)
    {
        // 基于MySQL全文检索（表已建ngram索引）做关键词匹配
        List<Faq> results = baseMapper.selectSimilarQuestions(scenicId, question);
        if (results != null && !results.isEmpty())
        {
            return results.get(0);
        }
        return null;
    }

    @Override
    public void incrementClickCount(Long id)
    {
        baseMapper.incrementClickCount(id);
    }

    @Override
    public void incrementHelpfulCount(Long id)
    {
        baseMapper.incrementHelpfulCount(id);
    }

    @Override
    public void incrementUnhelpfulCount(Long id)
    {
        baseMapper.incrementUnhelpfulCount(id);
    }

    @Override
    public List<Faq> getHotQuestions(Long scenicId, Integer limit)
    {
        // 使用 MyBatis-Plus 分页查询替代 .last("LIMIT")，避免SQL注入
        Page<Faq> page = new Page<>(1, limit != null ? limit : 10);
        Page<Faq> result = page(page, new LambdaQueryWrapper<Faq>()
                .eq(Faq::getScenicId, scenicId)
                .eq(Faq::getStatus, 1)
                .orderByDesc(Faq::getClickCount));
        return result.getRecords();
    }
}
