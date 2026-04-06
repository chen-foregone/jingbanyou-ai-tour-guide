package cn.edu.gdou.jingbanyou.tourist.graph.node;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * FAQ 润色节点（高频问题答案优化）
 * 任务：把 FAQ 库的固定标准答案，润色得更亲切、更符合 AI 数字人语气
 */
@Component
public class FaqAnswerPolishNode implements NodeAction {

    @Autowired
    private ChatClient.Builder builder;
    private ChatClient chatClient;

    public FaqAnswerPolishNode(ChatClient.Builder builder) {
        this.builder = builder;
        chatClient = builder.defaultSystem("你是一个亲切的景区导游助手。\n" +
                "\n" +
                "请根据以下 FAQ 标准答案，用更自然、口语化的方式回答游客的问题。\n" +
                "\n" +
                "要求：\n" +
                "- 保持答案的准确性，不要改变关键信息（时间、价格、地点等）\n" +
                "- 语气亲切，适当加入\"~\"\"！\"等语气符号使回答更生动\n" +
                "- 如果 FAQ 答案较长，可适当精简，突出重点\n" +
                "- 不超过100字\n" +
                "\n" +
                "游客问题：{question}\n" +
                "FAQ 标准答案：{faq_answer}")
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel("qwen3.5-flash") // Flash足够，润色简单话术不需要大模型
                        .withTemperature(0.4)        // 温度稍低，保证不偏离FAQ标准答案，只做语气优化
                        .withTopP(0.85)
                        .withMaxToken(256)          // 润色后的答案不需要太长
                        .build()).build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // TODO: 实现FAQ答案润色逻辑
        return new HashMap<>();
    }
}
