package cn.edu.gdou.jingbanyou.tourist.graph.pojo;

import lombok.Builder;
import lombok.Data;

/**
 * FAQ 预检结果
 */
@Data
@Builder
public class RagPrecheckResult {

    /**
     * 是否命中 FAQ
     */
    private boolean matched;

    /**
     * FAQ 标准答案
     */
    private String answer;

    /**
     * 匹配的标准问题
     */
    private String matchedQuestion;

    /**
     * 向量相似度分数
     */
    private Double score;

    /**
     * 命中时的提示信息
     */
    private String message;

    public static RagPrecheckResult noMatch() {
        return RagPrecheckResult.builder()
                .matched(false)
                .build();
    }

    public static RagPrecheckResult matched(String answer, String matchedQuestion, Double score) {
        return RagPrecheckResult.builder()
                .matched(true)
                .answer(answer)
                .matchedQuestion(matchedQuestion)
                .score(score)
                .message("命中 FAQ，直接返回")
                .build();
    }
}
