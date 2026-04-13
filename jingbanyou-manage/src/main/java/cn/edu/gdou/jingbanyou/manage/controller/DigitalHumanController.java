package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Log;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.core.page.TableDataInfo;
import cn.edu.gdou.jingbanyou.common.enums.BusinessType;
import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.manage.service.IDigitalHumanConfigService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 数字人配置管理
 *
 * @author jingbanyou
 */
@Slf4j
@PreAuthorize("@ss.hasRole('admin') or @ss.hasRole('scenic_admin')")
@RestController
@RequestMapping("/manage/digital-human")
public class DigitalHumanController extends BaseController {

    @Autowired
    private IDigitalHumanConfigService digitalHumanService;

    /** 查询数字人列表 */
    @GetMapping("/list")
    public TableDataInfo list() {
        startPage();
        List<DigitalHumanConfig> list = digitalHumanService.list();
        return getDataTable(list);
    }

    /** 查询数字人详情 */
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(digitalHumanService.getById(id));
    }

    /** 获取景区默认数字人 */
    @GetMapping("/scenic/{scenicId}/default")
    public AjaxResult getDefault(@PathVariable Long scenicId) {
        return success(digitalHumanService.getDefaultByScenicId(scenicId));
    }

    /** 新增数字人配置 */
    @Log(title = "数字人管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody DigitalHumanConfig config) {
        return toAjax(digitalHumanService.save(config));
    }

    /** 修改数字人配置 */
    @Log(title = "数字人管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody DigitalHumanConfig config) {
        return toAjax(digitalHumanService.updateById(config));
    }

    /** 设置默认数字人 */
    @Log(title = "数字人管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/set-default")
    public AjaxResult setDefault(@PathVariable Long id, @RequestParam Long scenicId) {
        digitalHumanService.setDefault(id, scenicId);
        return success();
    }

    /** 删除数字人配置 */
    @Log(title = "数字人管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        return toAjax(digitalHumanService.removeById(id));
    }
}
