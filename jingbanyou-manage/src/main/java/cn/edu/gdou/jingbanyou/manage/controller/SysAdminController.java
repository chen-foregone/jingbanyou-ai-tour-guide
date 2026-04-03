package cn.edu.gdou.jingbanyou.manage.controller;

import cn.edu.gdou.jingbanyou.common.annotation.Log;
import cn.edu.gdou.jingbanyou.common.constant.Constants;
import cn.edu.gdou.jingbanyou.common.core.controller.BaseController;
import cn.edu.gdou.jingbanyou.common.core.domain.AjaxResult;
import cn.edu.gdou.jingbanyou.common.core.page.TableDataInfo;
import cn.edu.gdou.jingbanyou.common.enums.BusinessType;
import cn.edu.gdou.jingbanyou.common.utils.uuid.IdUtils;
import cn.edu.gdou.jingbanyou.manage.dto.SysAdminLoginRequest;
import cn.edu.gdou.jingbanyou.manage.dto.SysAdminVO;
import cn.edu.gdou.jingbanyou.manage.entity.SysAdmin;
import cn.edu.gdou.jingbanyou.manage.service.ISysAdminService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统管理员 Controller
 *
 * @author jingbanyou
 */
@Slf4j
@RestController
@RequestMapping("/manage/admin")
public class SysAdminController extends BaseController
{
    @Autowired
    private ISysAdminService adminService;

    @Value("${token.secret:abcdefghijklmnopqrstuvwxyz}")
    private String secret;

    /**
     * 获取管理员列表
     */
    @GetMapping("/list")
    public TableDataInfo list(SysAdmin admin)
    {
        startPage();
        List<SysAdmin> list = adminService.list();
        return getDataTable(list.stream().map(this::toVO).collect(Collectors.toList()));
    }

    /**
     * 根据 ID 查询管理员
     */
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        SysAdmin admin = adminService.getById(id);
        return success(toVO(admin));
    }

    /**
     * 管理员登录
     */
    @PostMapping("/login")
    public AjaxResult login(@Valid @RequestBody SysAdminLoginRequest loginRequest, HttpServletRequest request)
    {
        log.info("管理员登录：{}", loginRequest.getUsername());

        SysAdmin admin = adminService.login(loginRequest.getUsername(), loginRequest.getPassword());
        if (admin == null)
        {
            return error("用户名或密码错误");
        }

        // 更新登录IP
        String ip = request.getRemoteAddr();
        adminService.updateLastLogin(admin.getId(), ip);

        // 生成JWT Token
        Map<String, Object> claims = new HashMap<>();
        claims.put(Constants.LOGIN_USER_KEY, IdUtils.fastUUID());
        claims.put("admin_id", admin.getId());
        claims.put("username", admin.getUsername());
        String token = Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();

        SysAdminVO vo = toVO(admin);
        vo.setToken(token);
        return success(vo);
    }

    /**
     * 新增管理员
     */
    @Log(title = "管理员管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody SysAdmin admin)
    {
        return toAjax(adminService.save(admin));
    }

    /**
     * 修改管理员
     */
    @Log(title = "管理员管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody SysAdmin admin)
    {
        return toAjax(adminService.updateById(admin));
    }

    /**
     * 删除管理员
     */
    @Log(title = "管理员管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        return toAjax(adminService.removeById(id));
    }

    /**
     * Entity 转 VO（排除密码）
     */
    private SysAdminVO toVO(SysAdmin admin)
    {
        if (admin == null)
        {
            return null;
        }
        return SysAdminVO.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .realName(admin.getRealName())
                .role(admin.getRole())
                .status(admin.getStatus())
                .lastLoginTime(admin.getLastLoginTime())
                .lastLoginIp(admin.getLastLoginIp())
                .createTime(admin.getCreateTime())
                .build();
    }
}
