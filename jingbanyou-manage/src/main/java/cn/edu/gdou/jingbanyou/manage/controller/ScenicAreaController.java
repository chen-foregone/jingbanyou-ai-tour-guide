package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.core.domain.R;
import cn.edu.gdou.jingbanyou.manage.entity.ScenicArea;
import cn.edu.gdou.jingbanyou.manage.service.IScenicAreaService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 景区基础信息 Controller
 */
@Slf4j
@RestController
@RequestMapping("/manage/scenic")
public class ScenicAreaController {

    @Autowired
    private IScenicAreaService scenicAreaService;

    /**
     * 获取景区列表
     */
    @GetMapping("/list")
    public R<List<ScenicArea>> list(ScenicArea scenicArea) {
        List<ScenicArea> list = scenicAreaService.list();
        return R.ok(list);
    }

    /**
     * 分页查询景区
     */
    @GetMapping("/page")
    public R<Page<ScenicArea>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ScenicArea> page = new Page<>(pageNum, pageSize);
        Page<ScenicArea> result = scenicAreaService.page(page);
        return R.ok(result);
    }

    /**
     * 根据 ID 查询景区
     */
    @GetMapping("/{id}")
    public R<ScenicArea> getInfo(@PathVariable Long id) {
        ScenicArea area = scenicAreaService.getById(id);
        return R.ok(area);
    }

    /**
     * 新增景区
     */
    @PostMapping
    public R<Boolean> add(@RequestBody ScenicArea scenicArea) {
        boolean result = scenicAreaService.save(scenicArea);
        return result ? R.ok() : R.fail("添加失败");
    }

    /**
     * 修改景区
     */
    @PutMapping
    public R<Boolean> edit(@RequestBody ScenicArea scenicArea) {
        boolean result = scenicAreaService.updateById(scenicArea);
        return result ? R.ok() : R.fail("修改失败");
    }

    /**
     * 删除景区
     */
    @DeleteMapping("/{id}")
    public R<Boolean> remove(@PathVariable Long id) {
        boolean result = scenicAreaService.removeById(id);
        return result ? R.ok() : R.fail("删除失败");
    }
}
