package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.entity.ScenicSpot;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 景区景点 Service 接口
 *
 * @author jingbanyou
 */
public interface IScenicSpotService extends IService<ScenicSpot> {

    /**
     * 删除景点并清理路线关联
     *
     * @param id 景点ID
     */
    void removeSpotWithRelations(Long id);
}
