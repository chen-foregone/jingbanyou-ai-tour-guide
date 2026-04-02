package cn.edu.gdou.jingbanyou.manage.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 知识库文档请求 DTO
 * 
 * @author JingbanYou Team
 * @date 2026-04-02
 */
@Data
public class KnowledgeDocRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文档 ID (更新时必填) */
    private Long id;

    /** 所属景区 ID */
    @NotNull(message = "所属景区不能为空")
    private Long scenicId;

    /** 文档标题 */
    @NotBlank(message = "文档标题不能为空")
    private String docTitle;

    /** 文档类型 */
    @NotBlank(message = "文档类型不能为空")
    private String docType;

    /** 文档内容 */
    @NotBlank(message = "文档内容不能为空")
    private String docContent;

    /** 文档摘要 */
    private String docSummary;

    /** 状态 */
    private Integer status;
}
