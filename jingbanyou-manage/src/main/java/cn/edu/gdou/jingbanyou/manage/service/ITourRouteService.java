package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.entity.TourRoute;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 个性化游览路线 Service 接口
 *
 * @author jingbanyou
 */
public interface ITourRouteService extends IService<TourRoute> {

    /**
     * 删除路线并清理景点关联
     *
     * @param id 路线ID
     */
    void removeRouteWithRelations(Long id);
}
