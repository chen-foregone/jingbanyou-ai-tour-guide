package cn.edu.gdou.jingbanyou.manage.dto.response;

import cn.edu.gdou.jingbanyou.manage.dto.HotQuestionVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 热门问答响应 VO
 *
 * @author jingbanyou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotQuestionsResponse implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 热门问答列表
     */
    private List<HotQuestionVO> hotQuestions;
}
