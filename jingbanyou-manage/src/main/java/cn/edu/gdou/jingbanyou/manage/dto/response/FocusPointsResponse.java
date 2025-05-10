package cn.edu.gdou.jingbanyou.manage.dto.response;

import cn.edu.gdou.jingbanyou.manage.dto.FocusPointVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 游客关注点响应 VO
 *
 * @author jingbanyou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FocusPointsResponse implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 关注点列表
     */
    private List<FocusPointVO> focusPoints;
}
