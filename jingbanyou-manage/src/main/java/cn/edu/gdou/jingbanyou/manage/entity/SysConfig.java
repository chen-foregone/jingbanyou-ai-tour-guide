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
 * 系统全局配置实体对象
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
@ToString
@TableName("manage_config")
public class SysConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置类型 text/json/number/boolean */
    private String configType;

    /** 配置分组 ai/ui/business/security */
    private String configGroup;

    /** 配置说明 */
    private String configDesc;

    /** 是否可编辑 0-否 1-是 */
    private Integer editable;

    /** 更新人 */
    private Long updater;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
