package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.manage.mapper.DigitalHumanConfigMapper;
import cn.edu.gdou.jingbanyou.manage.service.IDigitalHumanConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 数字人形象配置 Service 实现类
 */
@Slf4j
@Service
public class DigitalHumanConfigServiceImpl extends ServiceImpl<DigitalHumanConfigMapper, DigitalHumanConfig> implements IDigitalHumanConfigService {

    @Override
    public DigitalHumanConfig getDefaultByScenicId(Long scenicId) {
        return getOne(new LambdaQueryWrapper<DigitalHumanConfig>()
                .eq(DigitalHumanConfig::getScenicId, scenicId)
                .eq(DigitalHumanConfig::getIsDefault, 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id, Long scenicId) {
        // 取消当前默认
        update(new LambdaUpdateWrapper<DigitalHumanConfig>()
                .set(DigitalHumanConfig::getIsDefault, 0)
                .eq(DigitalHumanConfig::getScenicId, scenicId)
                .eq(DigitalHumanConfig::getIsDefault, 1));
        
        // 设置新默认
        update(new LambdaUpdateWrapper<DigitalHumanConfig>()
                .set(DigitalHumanConfig::getIsDefault, 1)
                .eq(DigitalHumanConfig::getId, id)
                .eq(DigitalHumanConfig::getScenicId, scenicId));
    }
}
