package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * AI 数字人形象配置 Service 接口
 *
 * @author jingbanyou
 */
public interface IDigitalHumanConfigService extends IService<DigitalHumanConfig> {

    /**
     * 获取景区默认数字人配置
     *
     * @param scenicId 景区ID
     * @return 默认数字人配置，未找到返回 null
     */
    DigitalHumanConfig getDefaultByScenicId(Long scenicId);

    /**
     * 设置默认数字人
     *
     * @param id 数字人配置ID
     * @param scenicId 景区ID
     */
    void setDefault(Long id, Long scenicId);

    /**
     * 查询数字人列表（可选按景区过滤）
     *
     * @param scenicId 景区ID（可选，为 null 时返回全部）
     * @return 数字人列表
     */
    List<DigitalHumanConfig> list(Long scenicId);
}
