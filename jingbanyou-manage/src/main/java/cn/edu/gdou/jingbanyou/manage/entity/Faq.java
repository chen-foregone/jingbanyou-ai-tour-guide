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
 * 常见问答实体对象（FAQ）
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
@ToString
@TableName("manage_faq")
public class Faq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 问答 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属景区 ID */
    private Long scenicId;

    /** 用户问题 */
    private String question;

    /** 问题关键词 */
    private String questionKeywords;

    /** 回答 */
    private String answer;

    /** 回答类型 text 直接显示，无需解析  /rich 用 MD 渲染器处理，支持加粗/列表/表格等/html 用 v-html / innerHTML 直接插入 */
    private String answerType;

    /** 关联景点 ID(可选) */
    private Long spotId;

    /** 相似问法 (JSON 数组扩展) */
    private String similarQuestions;

    /** 被咨询次数 */
    private Integer clickCount;

    /** 点赞数 */
    private Integer helpfulCount;

    /** 踩数 */
    private Integer unhelpfulCount;

    /** 问题向量 ID */
    private String vectorId;

    /** 相似度阈值 */
    private Double similarityThreshold;

    /** 创建人 */
    private Long creator;

    /** 更新人 */
    private Long updater;

    /** 状态 0-禁用 1-启用 */
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
