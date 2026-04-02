package cn.edu.gdou.jingbanyou.manage.mapper;

import cn.edu.gdou.jingbanyou.manage.entity.ScenicArea;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 景区基础信息 Mapper 接口
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Mapper
public interface ScenicAreaMapper extends BaseMapper<ScenicArea> {
    // MyBatis-Plus 已提供 CRUD 方法，无需额外定义
}
