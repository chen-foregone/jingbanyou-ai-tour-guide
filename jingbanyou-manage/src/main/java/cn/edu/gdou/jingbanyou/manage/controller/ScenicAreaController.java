package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Log;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.core.page.TableDataInfo;
import cn.edu.gdou.jingbanyou.common.enums.BusinessType;
import cn.edu.gdou.jingbanyou.common.utils.poi.ExcelUtil;
import cn.edu.gdou.jingbanyou.manage.dto.ScenicAreaVO;
import cn.edu.gdou.jingbanyou.manage.entity.ScenicArea;
import cn.edu.gdou.jingbanyou.manage.service.IScenicAreaService;
import cn.hutool.core.bean.BeanUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
public class ScenicAreaController extends BaseController {

    @Autowired
    private IScenicAreaService scenicAreaService;

    /**
     * 获取景区列表
     */
    @GetMapping("/list")
    public TableDataInfo list() {
        startPage();
        List<ScenicArea> list = scenicAreaService.list();
        List<ScenicAreaVO> voList = BeanUtil.copyToList(list, ScenicAreaVO.class);
        return getDataTable(voList);
    }

    /**
     * 根据 ID 查询景区
     */
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        ScenicArea area = scenicAreaService.getById(id);
        return success(BeanUtil.copyProperties(area, ScenicAreaVO.class));
    }

    /**
     * 新增景区
     */
    @Log(title = "景区管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody ScenicArea scenicArea) {
        return toAjax(scenicAreaService.save(scenicArea));
    }

    /**
     * 修改景区
     */
    @Log(title = "景区管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody ScenicArea scenicArea) {
        return toAjax(scenicAreaService.updateById(scenicArea));
    }

    /**
     * 删除景区
     */
    @Log(title = "景区管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        return toAjax(scenicAreaService.removeById(id));
    }

    /**
     * 导入赛题 Excel 数据（景点景区旅游数据行为分析数据.xlsx）
     * 流程：读取 attraction_name / attraction_type / attraction_content
     *       → 去重写入 manage_scenic_area
     *       → 景区介绍写入 manage_knowledge_doc（文史资料类型）
     */
    @Log(title = "景区数据导入", businessType = BusinessType.IMPORT)
    @PostMapping("/import")
    public AjaxResult importFromExcel(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return error("请上传文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            return error("仅支持 .xlsx 或 .xls 格式");
        }
        try {
            // creator 从 SecurityContext 取，这里写死 1L（后续可改为从登录上下文获取）
            String result = scenicAreaService.importFromExcel(file, 1L);
            return success(result);
        } catch (Exception e) {
            log.error("景区Excel导入失败", e);
            return error("导入失败: " + e.getMessage());
        }
    }

    /**
     * 下载赛题 Excel 模板（或示例数据）
     */
    @GetMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        // 赛题数据已提供，不需要生成模板，直接提示上传赛题数据即可
        ExcelUtil<ScenicAreaVO> util = new ExcelUtil<>(ScenicAreaVO.class);
        util.importTemplateExcel(response, "景区数据导入模板");
    }
}
