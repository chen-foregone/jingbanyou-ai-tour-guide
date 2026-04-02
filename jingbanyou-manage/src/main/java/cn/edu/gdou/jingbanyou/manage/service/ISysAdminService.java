package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.entity.SysAdmin;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 系统管理员 Service 接口
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
public interface ISysAdminService extends IService<SysAdmin> {

    /**
     * 根据用户名查询管理员
     */
    SysAdmin getByUsername(String username);

    /**
     * 用户登录
     */
    SysAdmin login(String username, String password);

    /**
     * 更新最后登录信息
     */
    void updateLastLogin(Long id, String ip);
}
