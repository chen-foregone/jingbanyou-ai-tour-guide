package cn.edu.gdou.jingbanyou.manage.dto.response;

import cn.edu.gdou.jingbanyou.manage.dto.OperationOverviewVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 今日实时概览响应 VO
 *
 * @author jingbanyou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationOverviewResponse implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 今日运营数据概览
     */
    private OperationOverviewVO overview;
}
