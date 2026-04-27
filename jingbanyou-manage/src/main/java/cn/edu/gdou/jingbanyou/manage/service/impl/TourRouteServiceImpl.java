package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.RouteSpotRelation;
import cn.edu.gdou.jingbanyou.manage.entity.TourRoute;
import cn.edu.gdou.jingbanyou.manage.mapper.RouteSpotRelationMapper;
import cn.edu.gdou.jingbanyou.manage.mapper.TourRouteMapper;
import cn.edu.gdou.jingbanyou.manage.service.ITourRouteService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个性化游览路线 Service 实现类
 *
 * @author jingbanyou
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourRouteServiceImpl extends ServiceImpl<TourRouteMapper, TourRoute> implements ITourRouteService {

    private final RouteSpotRelationMapper routeSpotRelationMapper;

    /**
     * 删除路线并清理景点关联
     *
     * @param id 路线ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRouteWithRelations(Long id) {
        routeSpotRelationMapper.delete(new LambdaQueryWrapper<RouteSpotRelation>()
                .eq(RouteSpotRelation::getRouteId, id));
        removeById(id);
        log.info("删除路线及其景点关联，routeId={}", id);
    }
}
