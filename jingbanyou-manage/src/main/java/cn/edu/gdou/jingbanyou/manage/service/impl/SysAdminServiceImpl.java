package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.SysAdmin;
import cn.edu.gdou.jingbanyou.manage.mapper.SysAdminMapper;
import cn.edu.gdou.jingbanyou.manage.service.ISysAdminService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 系统管理员 Service 实现类
 *
 * @author jingbanyou
 */
@Slf4j
@Service
public class SysAdminServiceImpl extends ServiceImpl<SysAdminMapper, SysAdmin> implements ISysAdminService
{
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public SysAdmin getByUsername(String username)
    {
        return baseMapper.selectByUsername(username);
    }

    @Override
    public SysAdmin login(String username, String password)
    {
        SysAdmin admin = getByUsername(username);
        if (admin == null)
        {
            log.warn("管理员不存在：{}", username);
            return null;
        }
        // BCrypt 密码校验
        if (!passwordEncoder.matches(password, admin.getPassword()))
        {
            log.warn("密码错误：{}", username);
            return null;
        }
        // 更新登录信息
        updateLastLogin(admin.getId(), null);
        return admin;
    }

    @Override
    public void updateLastLogin(Long id, String ip)
    {
        baseMapper.updateLastLogin(id, ip);
    }

    /**
     * 新增管理员时对密码进行加密
     */
    @Override
    public boolean save(SysAdmin entity)
    {
        if (entity.getPassword() != null)
        {
            entity.setPassword(passwordEncoder.encode(entity.getPassword()));
        }
        return super.save(entity);
    }
}
