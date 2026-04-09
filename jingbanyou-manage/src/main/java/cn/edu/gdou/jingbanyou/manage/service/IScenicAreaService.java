package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.dto.ScenicAreaExcelDTO;
import cn.edu.gdou.jingbanyou.manage.entity.ScenicArea;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 景区基础信息 Service 接口
 */
public interface IScenicAreaService extends IService<ScenicArea> {

    /**
     * 从 Excel 导入景区数据（去重入库）
     * 流程：读取景区数据 → 按名称去重 → 写入 manage_scenic_area
     *
     * @param file Excel 文件（赛题提供的景点景区旅游数据行为分析数据.xlsx）
     * @param creator 创建人 ID
     * @return 导入结果描述（成功数/跳过数）
     */
    String importFromExcel(MultipartFile file, Long creator);
}
