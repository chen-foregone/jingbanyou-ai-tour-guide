package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.DigitalHumanConfig;
import cn.edu.gdou.jingbanyou.manage.mapper.DigitalHumanConfigMapper;
import cn.edu.gdou.jingbanyou.manage.service.IDigitalHumanConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 数字人形象配置 Service 实现类
 *
 * @author jingbanyou
 */
@Slf4j
@Service
public class DigitalHumanConfigServiceImpl extends ServiceImpl<DigitalHumanConfigMapper, DigitalHumanConfig> implements IDigitalHumanConfigService {

    /**
     * 获取景区默认数字人配置
     *
     * @param scenicId 景区ID
     * @return 默认数字人配置，未找到返回 null
     */
    @Override
    public DigitalHumanConfig getDefaultByScenicId(Long scenicId) {
        return getOne(new LambdaQueryWrapper<DigitalHumanConfig>()
                .eq(DigitalHumanConfig::getScenicId, scenicId)
                .eq(DigitalHumanConfig::getIsDefault, 1));
    }

    /**
     * 设置默认数字人
     * 将指定 ID 的数字人设为景区默认，同时取消原默认
     *
     * @param id 数字人配置ID
     * @param scenicId 景区ID
     */
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
        log.info("设置默认数字人成功：id={}, scenicId={}", id, scenicId);
    }

    /**
     * 查询数字人列表（可选按景区过滤）
     *
     * @param scenicId 景区ID（可选，为 null 时返回全部）
     * @return 数字人列表
     */
    @Override
    public List<DigitalHumanConfig> list(Long scenicId) {
        if (scenicId == null) {
            return list();
        }
        return list(new LambdaQueryWrapper<DigitalHumanConfig>()
                .eq(DigitalHumanConfig::getScenicId, scenicId));
    }
}
