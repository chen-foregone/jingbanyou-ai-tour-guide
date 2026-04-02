package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.Faq;
import cn.edu.gdou.jingbanyou.manage.mapper.FaqMapper;
import cn.edu.gdou.jingbanyou.manage.service.IFaqService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 常见问答 Service 实现类（赛题要求：保障问答准确率 90%）
 */
@Slf4j
@Service
public class FaqServiceImpl extends ServiceImpl<FaqMapper, Faq> implements IFaqService {

    @Override
    public Faq matchSimilarQuestion(Long scenicId, String question) {
        // TODO: 使用向量相似度匹配 FAQ
        return getOne(new LambdaQueryWrapper<Faq>()
                .eq(Faq::getScenicId, scenicId));
    }

    @Override
    public void incrementClickCount(Long id) {
        baseMapper.incrementClickCount(id);
    }

    @Override
    public List<Faq> getHotQuestions(Long scenicId, Integer limit) {
        return list(new LambdaQueryWrapper<Faq>()
                .eq(Faq::getScenicId, scenicId)
                .orderByDesc(Faq::getClickCount)
                .last("LIMIT " + limit));
    }
}
