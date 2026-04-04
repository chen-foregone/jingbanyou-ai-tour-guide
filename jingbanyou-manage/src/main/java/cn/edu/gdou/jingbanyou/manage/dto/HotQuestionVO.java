package cn.edu.gdou.jingbanyou.manage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 热门问答 VO
 *
 * @author jingbanyou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotQuestionVO implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 问题内容 */
    private String question;

    /** 出现次数 */
    private Integer count;

    /** 排名 */
    private Integer rank;
}
