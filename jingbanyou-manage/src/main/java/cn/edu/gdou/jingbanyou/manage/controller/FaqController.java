package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.core.domain.R;
import cn.edu.gdou.jingbanyou.manage.entity.Faq;
import cn.edu.gdou.jingbanyou.manage.service.IFaqService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 常见问答管理 Controller（赛题要求：保障问答准确率 90%）
 */
@Slf4j
@RestController
@RequestMapping("/manage/faq")
public class FaqController {

    @Autowired
    private IFaqService faqService;

    /**
     * 获取 FAQ 列表
     */
    @GetMapping("/list")
    public R<List<Faq>> list(Faq faq) {
        List<Faq> list = faqService.list();
        return R.ok(list);
    }

    /**
     * 分页查询 FAQ
     */
    @GetMapping("/page")
    public R<Page<Faq>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            Faq faq) {
        Page<Faq> page = new Page<>(pageNum, pageSize);
        Page<Faq> result = faqService.page(page);
        return R.ok(result);
    }

    /**
     * 根据 ID 查询 FAQ
     */
    @GetMapping("/{id}")
    public R<Faq> getInfo(@PathVariable Long id) {
        Faq faq = faqService.getById(id);
        return R.ok(faq);
    }

    /**
     * 智能匹配相似问题（RAG 检索）
     */
    @GetMapping("/match")
    public R<Faq> matchQuestion(
            @RequestParam Long scenicId,
            @RequestParam String question) {
        
        Faq matchedFaq = faqService.matchSimilarQuestion(scenicId, question);
        return R.ok(matchedFaq);
    }

    /**
     * 新增 FAQ
     */
    @PostMapping
    public R<Boolean> add(@RequestBody Faq faq) {
        boolean result = faqService.save(faq);
        return result ? R.ok() : R.fail("添加失败");
    }

    /**
     * 修改 FAQ
     */
    @PutMapping
    public R<Boolean> edit(@RequestBody Faq faq) {
        boolean result = faqService.updateById(faq);
        return result ? R.ok() : R.fail("修改失败");
    }

    /**
     * 删除 FAQ
     */
    @DeleteMapping("/{id}")
    public R<Boolean> remove(@PathVariable Long id) {
        boolean result = faqService.removeById(id);
        return result ? R.ok() : R.fail("删除失败");
    }

    /**
     * 点赞
     */
    @PostMapping("/{id}/helpful")
    public R<Boolean> markHelpful(@PathVariable Long id) {
        // TODO: 增加 helpful_count
        return R.ok();
    }

    /**
     * 点踩
     */
    @PostMapping("/{id}/unhelpful")
    public R<Boolean> markUnhelpful(@PathVariable Long id) {
        // TODO: 增加 unhelpful_count
        return R.ok();
    }
}
