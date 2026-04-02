package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.SysAdmin;
import cn.edu.gdou.jingbanyou.manage.mapper.SysAdminMapper;
import cn.edu.gdou.jingbanyou.manage.service.ISysAdminService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 系统管理员 Service 实现类
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Slf4j
@Service
public class SysAdminServiceImpl extends ServiceImpl<SysAdminMapper, SysAdmin> implements ISysAdminService {

    @Override
    public SysAdmin getByUsername(String username) {
        return baseMapper.selectByUsername(username);
    }

    @Override
    public SysAdmin login(String username, String password) {
        SysAdmin admin = getByUsername(username);
        if (admin == null) {
            log.warn("管理员不存在：{}", username);
            return null;
        }
        
        // TODO: 密码验证（实际应使用 BCrypt 等加密方式）
        if (!password.equals(admin.getPassword())) {
            log.warn("密码错误：{}", username);
            return null;
        }
        
        // 更新登录信息
        updateLastLogin(admin.getId(), null);
        
        return admin;
    }

    @Override
    public void updateLastLogin(Long id, String ip) {
        baseMapper.updateLastLogin(id, ip);
    }
}
