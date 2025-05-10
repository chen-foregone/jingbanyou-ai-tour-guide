package cn.edu.gdou.jingbanyou.manage.dto.response;

import cn.edu.gdou.jingbanyou.manage.dto.SatisfactionTrendVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 满意度趋势响应 VO
 *
 * @author jingbanyou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfactionTrendResponse implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 满意度趋势数据列表
     */
    private List<SatisfactionTrendVO> trend;
}
