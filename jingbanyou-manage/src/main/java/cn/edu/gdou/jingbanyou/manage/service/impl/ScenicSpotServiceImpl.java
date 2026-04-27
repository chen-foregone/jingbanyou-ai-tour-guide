package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.RouteSpotRelation;
import cn.edu.gdou.jingbanyou.manage.entity.ScenicSpot;
import cn.edu.gdou.jingbanyou.manage.mapper.RouteSpotRelationMapper;
import cn.edu.gdou.jingbanyou.manage.mapper.ScenicSpotMapper;
import cn.edu.gdou.jingbanyou.manage.service.IScenicSpotService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 景区景点 Service 实现类
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScenicSpotServiceImpl extends ServiceImpl<ScenicSpotMapper, ScenicSpot> implements IScenicSpotService {

    private final RouteSpotRelationMapper routeSpotRelationMapper;

    /**
     * 删除景点并清理路线关联
     *
     * @param id 景点ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSpotWithRelations(Long id) {
        routeSpotRelationMapper.delete(new LambdaQueryWrapper<RouteSpotRelation>()
                .eq(RouteSpotRelation::getSpotId, id));
        removeById(id);
        log.info("删除景点及其路线关联，spotId={}", id);
    }
}
