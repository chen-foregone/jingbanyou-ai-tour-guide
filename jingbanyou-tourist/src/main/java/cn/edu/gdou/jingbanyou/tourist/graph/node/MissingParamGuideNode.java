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
 * 参数缺失引导节点
 * 任务：用自然、友好的语气引导游客补充缺失的路线参数
 */
@Component
public class MissingParamGuideNode implements NodeAction {

    @Autowired
    private ChatClient.Builder builder;
    private ChatClient chatClient;

    public MissingParamGuideNode(ChatClient.Builder builder) {
        this.builder = builder;
        chatClient = builder.defaultSystem("你是一个亲切的景区导游助手。\n" +
                "\n" +
                "游客想要规划游览路线，但提供的信息不完整。\n" +
                "缺少的参数：{missing_params}\n" +
                "\n" +
                "请用自然、友好的语气引导游客补充信息，不超过2句话。\n" +
                "\n" +
                "示例：\n" +
                "- 缺少起点：\"您好！请问您现在在景区哪个位置呢？这样我可以为您规划最合适的路线\"\n" +
                "- 缺少终点：\"您想去哪个景点呢？告诉我目的地，我马上为您规划路线！\"")
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel("qwen3.5-flash") // Flash足够，引导语简单
                        .withTemperature(0.7)        // 温度适中，让语气更自然友好
                        .withTopP(0.9)
                        .withMaxToken(128)          // 引导语不需要太长
                        .build()).build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // TODO: 实现参数缺失引导逻辑
        return new HashMap<>();
    }
}
