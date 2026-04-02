package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.core.domain.R;
import cn.edu.gdou.jingbanyou.manage.dto.KnowledgeDocRequest;
import cn.edu.gdou.jingbanyou.manage.dto.KnowledgeDocVO;
import cn.edu.gdou.jingbanyou.manage.entity.KnowledgeDoc;
import cn.edu.gdou.jingbanyou.manage.service.IKnownledgeDocService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库文档 Controller
 */
@Slf4j
@RestController
@RequestMapping("/manage/knowledge")
public class KnowledgeDocController {

    @Autowired
    private IKnownledgeDocService knowledgeDocService;

    /**
     * 获取知识库列表
     */
    @GetMapping("/list")
    public R<List<KnowledgeDocVO>> list(KnowledgeDoc doc) {
        List<KnowledgeDoc> list = knowledgeDocService.list();
        List<KnowledgeDocVO> voList = list.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return R.ok(voList);
    }

    /**
     * 分页查询知识库
     */
    @GetMapping("/page")
    public R<Page<KnowledgeDocVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            KnowledgeDocRequest request) {
        Page<KnowledgeDoc> page = new Page<>(pageNum, pageSize);
        Page<KnowledgeDoc> result = knowledgeDocService.page(page);
        
        Page<KnowledgeDocVO> voPage = new Page<>(pageNum, pageSize);
        BeanUtils.copyProperties(result, voPage, "records");
        voPage.setRecords(result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList()));
        
        return R.ok(voPage);
    }

    /**
     * 根据 ID 查询知识库
     */
    @GetMapping("/{id}")
    public R<KnowledgeDocVO> getInfo(@PathVariable Long id) {
        KnowledgeDoc doc = knowledgeDocService.getById(id);
        return R.ok(convertToVO(doc));
    }

    /**
     * 新增知识库
     */
    @PostMapping
    public R<Boolean> add(@RequestBody KnowledgeDocRequest request) {
        KnowledgeDoc doc = new KnowledgeDoc();
        BeanUtils.copyProperties(request, doc);
        boolean result = knowledgeDocService.save(doc);
        return result ? R.ok() : R.fail("添加失败");
    }

    /**
     * 修改知识库
     */
    @PutMapping
    public R<Boolean> edit(@RequestBody KnowledgeDocRequest request) {
        KnowledgeDoc doc = new KnowledgeDoc();
        BeanUtils.copyProperties(request, doc);
        boolean result = knowledgeDocService.updateById(doc);
        return result ? R.ok() : R.fail("修改失败");
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/{id}")
    public R<Boolean> remove(@PathVariable Long id) {
        boolean result = knowledgeDocService.removeById(id);
        return result ? R.ok() : R.fail("删除失败");
    }

    /**
     * Entity 转 VO
     */
    private KnowledgeDocVO convertToVO(KnowledgeDoc doc) {
        KnowledgeDocVO vo = new KnowledgeDocVO();
        BeanUtils.copyProperties(doc, vo);
        return vo;
    }
}
