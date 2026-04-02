package cn.edu.gdou.jingbanyou.manage.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 知识库文档响应 VO
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
public class KnowledgeDocVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文档 ID */
    private Long id;

    /** 所属景区 ID */
    private Long scenicId;

    /** 景区名称 */
    private String scenicName;

    /** 文档标题 */
    private String docTitle;

    /** 文档类型 */
    private String docType;

    /** 文档内容（列表页可截断） */
    private String docContent;

    /** 文档摘要 */
    private String docSummary;

    /** 字数统计 */
    private Integer wordCount;

    /** 切片数量 */
    private Integer chunkCount;

    /** 是否已向量化 */
    private Integer vectorized;

    /** 被引用次数 */
    private Integer viewCount;

    /** 创建人姓名 */
    private String creatorName;

    /** 状态 */
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
