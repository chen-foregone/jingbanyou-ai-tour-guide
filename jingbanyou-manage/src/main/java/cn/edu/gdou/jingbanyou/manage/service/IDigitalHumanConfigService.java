package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * AI 数字人形象配置 Service 接口
 */
public interface IDigitalHumanConfigService extends IService<DigitalHumanConfig> {

    /**
     * 获取景区默认数字人配置
     */
    DigitalHumanConfig getDefaultByScenicId(Long scenicId);

    /**
     * 设置默认数字人
     */
    void setDefault(Long id, Long scenicId);
}
