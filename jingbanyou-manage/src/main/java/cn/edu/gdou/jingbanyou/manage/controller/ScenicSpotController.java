package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Log;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.core.page.TableDataInfo;
import cn.edu.gdou.jingbanyou.common.enums.BusinessType;
import cn.edu.gdou.jingbanyou.manage.entity.ScenicSpot;
import cn.edu.gdou.jingbanyou.manage.service.IScenicSpotService;
import com.github.pagehelper.PageInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 景区景点管理
 *
 * @author jingbanyou
 */
@Slf4j
@RestController
@RequestMapping("/manage/spot")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasRole('admin') or @ss.hasRole('scenic_admin')")
public class ScenicSpotController extends BaseController {

    private final IScenicSpotService scenicSpotService;

    /**
     * 查询景点列表
     *
     * @param scenicId 景区ID（可选，用于过滤）
     * @return 分页后的景点列表
     */
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) Long scenicId) {
        startPage();
        List<ScenicSpot> list;
        if (scenicId != null) {
            list = scenicSpotService.lambdaQuery()
                    .eq(ScenicSpot::getScenicId, scenicId)
                    .orderByAsc(ScenicSpot::getSort)
                    .list();
        } else {
            list = scenicSpotService.lambdaQuery()
                    .orderByAsc(ScenicSpot::getSort)
                    .list();
        }
        return getDataTable(list, new PageInfo(list).getTotal());
    }

    /**
     * 查询景点详情
     *
     * @param id 景点ID
     * @return 景点详情
     */
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(scenicSpotService.getById(id));
    }

    /**
     * 新增景点
     *
     * @param spot 景点信息
     * @return 操作结果
     */
    @Log(title = "景点管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody ScenicSpot spot) {
        return toAjax(scenicSpotService.save(spot));
    }

    /**
     * 修改景点
     *
     * @param spot 景点信息
     * @return 操作结果
     */
    @Log(title = "景点管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody ScenicSpot spot) {
        return toAjax(scenicSpotService.updateById(spot));
    }

    /**
     * 删除景点
     *
     * @param id 景点ID
     * @return 操作结果
     */
    @Log(title = "景点管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        scenicSpotService.removeSpotWithRelations(id);
        return success();
    }
}
