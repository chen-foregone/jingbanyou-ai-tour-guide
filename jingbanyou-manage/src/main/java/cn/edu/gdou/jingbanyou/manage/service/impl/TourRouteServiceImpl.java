package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.TourRoute;
import cn.edu.gdou.jingbanyou.manage.mapper.TourRouteMapper;
import cn.edu.gdou.jingbanyou.manage.service.ITourRouteService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 个性化游览路线 Service 实现类
 *
 * @author jingbanyou
 */
@Slf4j
@Service
public class TourRouteServiceImpl extends ServiceImpl<TourRouteMapper, TourRoute> implements ITourRouteService {
}
