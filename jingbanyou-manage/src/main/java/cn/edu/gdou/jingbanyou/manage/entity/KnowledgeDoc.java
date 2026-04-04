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
 * 景区知识库文档实体对象（RAG 数据源）
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
@ToString
@TableName("manage_knowledge_doc")
public class KnowledgeDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文档 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属景区 ID */
    private Long scenicId;

    /** 文档标题 */
    private String docTitle;

    /** 文档类型 讲解词/文史资料/攻略/公告 */
    private String docType;

    /** 文档完整内容 */
    private String docContent;

    /** 文档摘要 (自动提取) */
    private String docSummary;

    /** 附件地址 */
    private String docFile;

    /** 文件大小 (字节) */
    private Long fileSize;

    /** 字数统计 */
    private Integer wordCount;

    /** 切片数量 */
    private Integer chunkCount;

    /** 是否已向量化 0-否 1-是 */
    private Integer vectorized;

    /** Embedding 模型版本 */
    private String embeddingModel;

    /** 创建人 */
    private Long creator;

    /** 最后更新人 */
    private Long updater;

    /** 被引用次数 */
    private Integer viewCount;

    /** 状态 0-禁用 1-启用 */
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
