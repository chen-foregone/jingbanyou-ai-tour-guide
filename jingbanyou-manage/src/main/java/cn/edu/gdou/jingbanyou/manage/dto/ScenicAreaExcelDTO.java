package cn.edu.gdou.jingbanyou.manage.dto;

import cn.edu.gdou.jingbanyou.common.annotation.Excel;
import lombok.Data;

/**
 * 景区导入 Excel DTO（对应赛题提供的景点景区旅游数据行为分析数据.xlsx）
 * 只映射需要用到的字段，忽略游客行为数据列
 *
 * @author jingbanyou
 */
@Data
public class ScenicAreaExcelDTO {

    /** 景区名称 → scenic_name */
    @Excel(name = "景区名称", sort = 4)
    private String attractionName;

    /** 景区详细介绍 → scenic_desc / knowledge_doc.content */
    @Excel(name = "景区详细介绍", sort = 5)
    private String attractionContent;

    /** 景区类型（如主题乐园/博物馆/古镇水乡）→ scenic_desc */
    @Excel(name = "景区类型", sort = 6)
    private String attractionType;
}
