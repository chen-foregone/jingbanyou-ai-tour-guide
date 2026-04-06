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
 * 知识库问答节点（景区详细介绍生成）
 * 任务：基于景区知识库内容，生成详细、准确、有吸引力的景点介绍 / 导览词
 */
@Component
public class ScenicKnowledgeAnswerGeneratorNode implements NodeAction {

    @Autowired
    private ChatClient.Builder builder;
    private ChatClient chatClient;

    public ScenicKnowledgeAnswerGeneratorNode(ChatClient.Builder builder) {
        this.builder = builder;
        chatClient = builder.defaultSystem("你是一个专业的自然风景区导游助手，拥有丰富的景区知识。\n" +
                "\n" +
                "请根据以下从景区知识库中检索到的参考资料，准确回答游客的问题。\n" +
                "\n" +
                "要求：\n" +
                "- 只根据提供的参考资料回答，不要编造信息\n" +
                "- 如果参考资料中没有相关信息，直接说\"这个问题我暂时还不太清楚，建议您咨询景区工作人员\"\n" +
                "- 语气亲切专业，适合自然风景区的氛围\n" +
                "- 回答简洁，不超过150字\n" +
                "\n" +
                "参考资料：\n" +
                "{retrieved_docs}\n" +
                "\n" +
                "历史对话（最近3轮）：\n" +
                "{history}\n" +
                "\n" +
                "游客问题：{question}")
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel("qwen3.6-plus")  // 用Plus，生成的内容更丰富、逻辑更连贯
                        .withTemperature(0.75)       // 温度稍高，让导览词更生动、有感染力
                        .withTopP(0.92)              // Top P较高，允许更丰富的词汇和表达
                        .withMaxToken(1024)         // 生成详细的景点介绍，需要长文本支持
                        .build()).build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // TODO: 实现景区知识答案生成逻辑
        return new HashMap<>();
    }
}
