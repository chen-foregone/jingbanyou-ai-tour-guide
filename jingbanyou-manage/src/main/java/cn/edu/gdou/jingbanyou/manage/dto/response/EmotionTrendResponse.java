package cn.edu.gdou.jingbanyou.manage.dto.response;

import cn.edu.gdou.jingbanyou.manage.dto.EmotionTrendVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 情感趋势响应 VO
 *
 * @author jingbanyou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionTrendResponse implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 情感趋势数据列表
     */
    private List<EmotionTrendVO> trend;
}
