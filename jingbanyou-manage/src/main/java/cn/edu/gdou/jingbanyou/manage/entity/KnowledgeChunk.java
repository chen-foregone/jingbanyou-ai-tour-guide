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
 * 知识文档切片实体对象（RAG 核心检索单元）
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
@ToString
@TableName("manage_knowledge_chunk")
public class KnowledgeChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 切片 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属文档 ID */
    private Long docId;

    /** 切片序号 (从 0 开始) */
    private Integer chunkIndex;

    /** 切片内容 */
    private String chunkContent;

    /** Token 数量 */
    private Integer chunkTokens;

    /** 向量 ID(ChromaDB/Milvus) */
    private String vectorId;

    /** Embedding 模型版本 */
    private String embeddingVersion;

    /** 元数据 (页码/章节/标签等，JSON) */
    private String metadata;

    /** 平均相似度 (用于优化) */
    private Double similarityAvg;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
