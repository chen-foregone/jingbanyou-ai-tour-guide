package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Log;
import org.springframework.security.access.prepost.PreAuthorize;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.core.page.TableDataInfo;
import cn.edu.gdou.jingbanyou.common.enums.BusinessType;
import cn.edu.gdou.jingbanyou.manage.entity.Faq;
import cn.edu.gdou.jingbanyou.manage.service.IFaqService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 常见问答管理 Controller
 *
 * @author jingbanyou
 */
@Slf4j
@PreAuthorize("@ss.hasRole('admin') or @ss.hasRole('scenic_admin')")
@RestController
@RequestMapping("/manage/faq")
public class FaqController extends BaseController
{
    @Autowired
    private IFaqService faqService;

    /**
     * 获取 FAQ 列表
     */
    @GetMapping("/list")
    public TableDataInfo list()
    {
        startPage();
        List<Faq> list = faqService.list();
        return getDataTable(list);
    }

    /**
     * 根据 ID 查询 FAQ
     */
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(faqService.getById(id));
    }

    /**
     * 智能匹配相似问题（RAG 检索）
     */
    @GetMapping("/match")
    public AjaxResult matchQuestion(@RequestParam Long scenicId, @RequestParam String question)
    {
        Faq matchedFaq = faqService.matchSimilarQuestion(scenicId, question);
        return success(matchedFaq);
    }

    /**
     * 获取热门问答
     */
    @GetMapping("/hot")
    public AjaxResult hotQuestions(@RequestParam Long scenicId,
                                   @RequestParam(defaultValue = "10") Integer limit)
    {
        List<Faq> hotList = faqService.getHotQuestions(scenicId, limit);
        return success(hotList);
    }

    /**
     * 新增 FAQ
     */
    @Log(title = "FAQ管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody Faq faq)
    {
        boolean saved = faqService.save(faq);
        if (saved)
        {
            faqService.vectorizeFaq(faq);
        }
        return toAjax(saved);
    }

    /**
     * 修改 FAQ
     */
    @Log(title = "FAQ管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody Faq faq)
    {
        boolean updated = faqService.updateById(faq);
        if (updated)
        {
            faqService.vectorizeFaq(faq);
        }
        return toAjax(updated);
    }

    /**
     * 删除 FAQ
     */
    @Log(title = "FAQ管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        return toAjax(faqService.removeById(id));
    }

    /**
     * 点赞
     */
    @PostMapping("/{id}/helpful")
    public AjaxResult markHelpful(@PathVariable Long id)
    {
        faqService.incrementHelpfulCount(id);
        return success();
    }

    /**
     * 点踩
     */
    @PostMapping("/{id}/unhelpful")
    public AjaxResult markUnhelpful(@PathVariable Long id)
    {
        faqService.incrementUnhelpfulCount(id);
        return success();
    }
}
