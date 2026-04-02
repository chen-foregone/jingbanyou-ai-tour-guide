package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.core.domain.R;
import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.manage.service.IDigitalHumanConfigService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 数字人形象配置 Controller（赛题要求核心功能）
 * 
 * 功能：管理数字人的外观、服装、声音等配置，使其贴合景区文化特色
 */
@Slf4j
@RestController
@RequestMapping("/manage/digital-human")
public class DigitalHumanController {

    @Autowired
    private IDigitalHumanConfigService digitalHumanService;

    /**
     * 获取数字人列表
     */
    @GetMapping("/list")
    public R<List<DigitalHumanConfig>> list(DigitalHumanConfig config) {
        List<DigitalHumanConfig> list = digitalHumanService.list();
        return R.ok(list);
    }

    /**
     * 分页查询数字人
     */
    @GetMapping("/page")
    public R<Page<DigitalHumanConfig>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            DigitalHumanConfig config) {
        Page<DigitalHumanConfig> page = new Page<>(pageNum, pageSize);
        Page<DigitalHumanConfig> result = digitalHumanService.page(page);
        return R.ok(result);
    }

    /**
     * 根据 ID 查询数字人详情
     */
    @GetMapping("/{id}")
    public R<DigitalHumanConfig> getInfo(@PathVariable Long id) {
        DigitalHumanConfig config = digitalHumanService.getById(id);
        return R.ok(config);
    }

    /**
     * 获取景区默认数字人
     */
    @GetMapping("/scenic/{scenicId}/default")
    public R<DigitalHumanConfig> getDefault(@PathVariable Long scenicId) {
        DigitalHumanConfig config = digitalHumanService.getDefaultByScenicId(scenicId);
        return R.ok(config);
    }

    /**
     * 新增数字人配置
     */
    @PostMapping
    public R<Boolean> add(@RequestBody DigitalHumanConfig config) {
        log.info("新增数字人配置：{}", config.getHumanName());
        boolean result = digitalHumanService.save(config);
        return result ? R.ok() : R.fail("添加失败");
    }

    /**
     * 修改数字人配置
     */
    @PutMapping
    public R<Boolean> edit(@RequestBody DigitalHumanConfig config) {
        boolean result = digitalHumanService.updateById(config);
        return result ? R.ok() : R.fail("修改失败");
    }

    /**
     * 设置默认数字人
     */
    @PostMapping("/{id}/set-default")
    public R<Boolean> setDefault(
            @PathVariable Long id,
            @RequestParam Long scenicId) {
        log.info("设置默认数字人：id={}, scenicId={}", id, scenicId);
        digitalHumanService.setDefault(id, scenicId);
        return R.ok();
    }

    /**
     * 删除数字人配置
     */
    @DeleteMapping("/{id}")
    public R<Boolean> remove(@PathVariable Long id) {
        boolean result = digitalHumanService.removeById(id);
        return result ? R.ok() : R.fail("删除失败");
    }
}
