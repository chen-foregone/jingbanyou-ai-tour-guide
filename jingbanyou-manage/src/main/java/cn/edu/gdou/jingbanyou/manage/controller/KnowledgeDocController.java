package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Log;
import org.springframework.security.access.prepost.PreAuthorize;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.core.page.TableDataInfo;
import cn.edu.gdou.jingbanyou.common.enums.BusinessType;
import cn.edu.gdou.jingbanyou.manage.dto.KnowledgeDocRequest;
import cn.edu.gdou.jingbanyou.manage.dto.KnowledgeDocVO;
import cn.edu.gdou.jingbanyou.manage.entity.KnowledgeDoc;
import cn.edu.gdou.jingbanyou.manage.service.IKnowledgeDocService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库文档 Controller
 *
 * @author jingbanyou
 */
@Slf4j
@PreAuthorize("@ss.hasRole('admin') or @ss.hasRole('scenic_admin')")
@RestController
@RequestMapping("/manage/knowledge")
public class KnowledgeDocController extends BaseController
{
    @Autowired
    private IKnowledgeDocService knowledgeDocService;

    /**
     * 获取知识库列表
     */
    @GetMapping("/list")
    public TableDataInfo list()
    {
        startPage();
        List<KnowledgeDoc> list = knowledgeDocService.list();
        return getDataTable(list.stream().map(this::convertToVO).collect(Collectors.toList()));
    }

    /**
     * 根据 ID 查询知识库
     */
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        KnowledgeDoc doc = knowledgeDocService.getById(id);
        return success(convertToVO(doc));
    }

    /**
     * 新增知识库
     */
    @Log(title = "知识库管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody KnowledgeDocRequest request)
    {
        KnowledgeDoc doc = new KnowledgeDoc();
        BeanUtils.copyProperties(request, doc);
        return toAjax(knowledgeDocService.save(doc));
    }

    /**
     * 修改知识库
     */
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody KnowledgeDocRequest request)
    {
        KnowledgeDoc doc = new KnowledgeDoc();
        BeanUtils.copyProperties(request, doc);
        return toAjax(knowledgeDocService.updateById(doc));
    }

    /**
     * 删除知识库（同步清理 Redis 向量 + MySQL chunk 记录）
     */
    @Log(title = "知识库管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        knowledgeDocService.removeDocWithVector(id);
        return success();
    }

    /**
     * 文档向量化
     */
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/vectorize")
    public AjaxResult vectorize(@PathVariable Long id)
    {
        knowledgeDocService.vectorizeDoc(id);
        return success();
    }

    /**
     * 批量向量化（所有 vectorized=0 的文档）
     */
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @PostMapping("/vectorize/batch")
    public AjaxResult batchVectorize() {
        int count = knowledgeDocService.batchVectorize();
        return success("批量向量化完成，共处理 " + count + " 条文档");
    }

    /**
     * 按景区批量向量化（指定景区下 vectorized=0 的文档）
     */
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @PostMapping("/vectorize/batch/{scenicId}")
    public AjaxResult batchVectorizeByScenic(@PathVariable Long scenicId) {
        int count = knowledgeDocService.batchVectorizeByScenic(scenicId);
        return success("景区 " + scenicId + " 批量向量化完成，共处理 " + count + " 条文档");
    }

    /**
     * Entity 转 VO
     */
    private KnowledgeDocVO convertToVO(KnowledgeDoc doc)
    {
        KnowledgeDocVO vo = new KnowledgeDocVO();
        BeanUtils.copyProperties(doc, vo);
        return vo;
    }
}
