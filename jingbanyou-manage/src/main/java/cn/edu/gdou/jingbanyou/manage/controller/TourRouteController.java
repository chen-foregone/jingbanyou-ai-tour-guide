package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Log;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.core.page.TableDataInfo;
import cn.edu.gdou.jingbanyou.common.enums.BusinessType;
import cn.edu.gdou.jingbanyou.manage.entity.TourRoute;
import cn.edu.gdou.jingbanyou.manage.service.ITourRouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 个性化游览路线管理
 *
 * @author jingbanyou
 */
@Slf4j
@RestController
@RequestMapping("/manage/route")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasRole('admin') or @ss.hasRole('scenic_admin')")
public class TourRouteController extends BaseController {

    private final ITourRouteService tourRouteService;

    /**
     * 查询路线列表
     *
     * @param scenicId 景区ID（可选，用于过滤）
     * @return 分页后的路线列表
     */
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) Long scenicId) {
        startPage();
        List<TourRoute> list;
        if (scenicId != null) {
            list = tourRouteService.lambdaQuery()
                    .eq(TourRoute::getScenicId, scenicId)
                    .orderByAsc(TourRoute::getSort)
                    .list();
        } else {
            list = tourRouteService.lambdaQuery()
                    .orderByAsc(TourRoute::getSort)
                    .list();
        }
        return getDataTable(list);
    }

    /**
     * 查询路线详情
     *
     * @param id 路线ID
     * @return 路线详情
     */
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(tourRouteService.getById(id));
    }

    /**
     * 新增路线
     *
     * @param route 路线信息
     * @return 操作结果
     */
    @Log(title = "路线管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody TourRoute route) {
        return toAjax(tourRouteService.save(route));
    }

    /**
     * 修改路线
     *
     * @param route 路线信息
     * @return 操作结果
     */
    @Log(title = "路线管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody TourRoute route) {
        return toAjax(tourRouteService.updateById(route));
    }

    /**
     * 删除路线
     *
     * @param id 路线ID
     * @return 操作结果
     */
    @Log(title = "路线管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        return toAjax(tourRouteService.removeById(id));
    }
}
