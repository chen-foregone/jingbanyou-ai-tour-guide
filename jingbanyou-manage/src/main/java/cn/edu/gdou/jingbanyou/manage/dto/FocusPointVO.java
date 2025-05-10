package cn.edu.gdou.jingbanyou.manage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 游客关注点 VO（意图类型分布单条数据）
 *
 * @author jingbanyou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FocusPointVO implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 意图类型（如：景点查询、路线规划、历史文化） */
    private String intentType;

    /** 该类型的交互次数 */
    private Integer count;
}
