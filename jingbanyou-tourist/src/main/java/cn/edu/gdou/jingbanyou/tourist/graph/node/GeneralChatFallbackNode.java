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
 * 闲聊兜底节点（非景区问题 / 投诉处理）
 * 任务：处理游客的闲聊、投诉、天气等非景区问题，要求语气友好、响应快
 */
@Component
public class GeneralChatFallbackNode implements NodeAction {

    @Autowired
    private ChatClient.Builder builder;
    private ChatClient chatClient;

    public GeneralChatFallbackNode(ChatClient.Builder builder) {
        this.builder = builder;
        chatClient = builder.defaultSystem("你是一个自然风景区的AI导游助手，名字叫\"小景\"。\n" +
                "\n" +
                "游客发来了一条与景区问答或路线规划无关的消息，可能是闲聊、投诉或建议。\n" +
                "\n" +
                "处理规则：\n" +
                "- 闲聊：简短友好地回应，并自然地引导回景区话题\n" +
                "- 投诉：表示理解和歉意，告知可联系景区服务中心（不要承诺具体赔偿）\n" +
                "- 建议：表示感谢，告知会反馈给景区管理方\n" +
                "\n" +
                "游客消息：{question}\n" +
                "\n" +
                "回复不超过80字，语气温和亲切。")
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel("qwen3.5-flash") // Flash足够，闲聊不需要复杂推理，速度优先
                        .withTemperature(0.9)        // 温度很高，让回复更活泼、更有人情味
                        .withTopP(0.95)              // Top P很高，允许多样化的闲聊回复
                        .withMaxToken(256)          // 闲聊回复不需要太长
                        .build()).build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // TODO: 实现通用聊天回退逻辑
        return new HashMap<>();
    }
}
