package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.core.domain.R;
import cn.edu.gdou.jingbanyou.manage.entity.SysAdmin;
import cn.edu.gdou.jingbanyou.manage.service.ISysAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 系统管理员 Controller
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Slf4j
@RestController
@RequestMapping("/manage/admin")
public class SysAdminController {

    @Autowired
    private ISysAdminService adminService;

    /**
     * 获取管理员列表
     */
    @GetMapping("/list")
    public R<List<SysAdmin>> list() {
        List<SysAdmin> list = adminService.list();
        return R.ok(list);
    }

    /**
     * 根据 ID 查询管理员
     */
    @GetMapping("/{id}")
    public R<SysAdmin> getInfo(@PathVariable Long id) {
        SysAdmin admin = adminService.getById(id);
        return R.ok(admin);
    }

    /**
     * 管理员登录
     */
    @PostMapping("/login")
    public R<SysAdmin> login(@RequestParam String username, 
                             @RequestParam String password,
                             HttpServletRequest request) {
        log.info("管理员登录：{}", username);
        
        String ip = request.getRemoteAddr();
        SysAdmin admin = adminService.login(username, password);
        
        if (admin == null) {
            return R.fail("用户名或密码错误");
        }
        
        // TODO: 生成 Token
        
        return R.ok(admin);
    }

    /**
     * 新增管理员
     */
    @PostMapping
    public R<Boolean> add(@RequestBody SysAdmin admin) {
        boolean result = adminService.save(admin);
        return result ? R.ok() : R.fail("添加失败");
    }

    /**
     * 修改管理员
     */
    @PutMapping
    public R<Boolean> edit(@RequestBody SysAdmin admin) {
        boolean result = adminService.updateById(admin);
        return result ? R.ok() : R.fail("修改失败");
    }

    /**
     * 删除管理员
     */
    @DeleteMapping("/{id}")
    public R<Boolean> remove(@PathVariable Long id) {
        boolean result = adminService.removeById(id);
        return result ? R.ok() : R.fail("删除失败");
    }
}
