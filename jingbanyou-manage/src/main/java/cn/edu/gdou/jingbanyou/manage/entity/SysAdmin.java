package cn.edu.gdou.jingbanyou.manage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.Date;

/**
 * 系统管理员实体对象
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
@ToString
@TableName("manage_admin")
public class SysAdmin implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 管理员 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String username;

    /** 登录密码 (加密存储) */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 角色 1-普通管理员 2-超级管理员 */
    private Integer role;

    /** 状态 0-禁用 1-正常 */
    private Integer status;

    /** 最后登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastLoginTime;

    /** 最后登录 IP */
    private String lastLoginIp;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
