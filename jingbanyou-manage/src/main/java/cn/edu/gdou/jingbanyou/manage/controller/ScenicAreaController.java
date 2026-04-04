package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Log;
import org.springframework.security.access.prepost.PreAuthorize;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.core.page.TableDataInfo;
import cn.edu.gdou.jingbanyou.common.enums.BusinessType;
import cn.edu.gdou.jingbanyou.manage.entity.ScenicArea;
import cn.edu.gdou.jingbanyou.manage.service.IScenicAreaService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 景区基础信息 Controller
 *
 * @author jingbanyou
 */
@Slf4j
@PreAuthorize("@ss.hasRole('admin') or @ss.hasRole('scenic_admin')")
@RestController
@RequestMapping("/manage/scenic")
public class ScenicAreaController extends BaseController
{
    @Autowired
    private IScenicAreaService scenicAreaService;

    /**
     * 获取景区列表
     */
    @GetMapping("/list")
    public TableDataInfo list()
    {
        startPage();
        List<ScenicArea> list = scenicAreaService.list();
        return getDataTable(list);
    }

    /**
     * 根据 ID 查询景区
     */
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(scenicAreaService.getById(id));
    }

    /**
     * 新增景区
     */
    @Log(title = "景区管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody ScenicArea scenicArea)
    {
        return toAjax(scenicAreaService.save(scenicArea));
    }

    /**
     * 修改景区
     */
    @Log(title = "景区管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody ScenicArea scenicArea)
    {
        return toAjax(scenicAreaService.updateById(scenicArea));
    }

    /**
     * 删除景区
     */
    @Log(title = "景区管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        return toAjax(scenicAreaService.removeById(id));
    }
}
