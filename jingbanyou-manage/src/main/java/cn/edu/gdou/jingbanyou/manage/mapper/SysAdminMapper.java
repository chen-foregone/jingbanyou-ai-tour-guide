package cn.edu.gdou.jingbanyou.manage.mapper;

import cn.edu.gdou.jingbanyou.manage.entity.SysAdmin;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 系统管理员 Mapper 接口
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Mapper
public interface SysAdminMapper extends BaseMapper<SysAdmin> {

    /**
     * 根据用户名查询管理员
     */
    SysAdmin selectByUsername(@Param("username") String username);

    /**
     * 更新最后登录信息
     */
    int updateLastLogin(@Param("id") Long id, @Param("ip") String ip);
}
