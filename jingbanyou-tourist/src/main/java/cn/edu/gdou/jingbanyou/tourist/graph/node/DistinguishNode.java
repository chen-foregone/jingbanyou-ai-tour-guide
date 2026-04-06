package cn.edu.gdou.jingbanyou.tourist.graph.node;

import com.alibaba.cloud.ai.dashscope.api.DashScopeResponseFormat;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 问题分类器节点（意图识别）
 * 任务：仅做 3 分类，输出 JSON，要求极度稳定、零随机、速度快
 */
@Component
public class DistinguishNode implements NodeAction {

    @Autowired
    private ChatClient.Builder builder;
    private ChatClient chatClient;

    public DistinguishNode(ChatClient.Builder builder) {
        this.builder = builder;
        chatClient = builder.defaultSystem("你是一个景区智能导游助手的意图分类器。\n" +
                "\n" +
                "请根据游客的问题，结合历史对话上下文，将其分类为以下三类之一：\n" +
                "\n" +
                "- route_plan：游客想要规划游览路线，包含\"怎么走\"\"路线\"\"从A到B\"\"导航\"等意图\n" +
                "- spot_question：游客询问景区内具体景点、自然景观、动植物、历史背景、开放时间、门票等信息\n" +
                "- complex_other：闲聊、投诉、建议、与景区无关的问题，或无法归入前两类的问题\n" +
                "\n" +
                "历史对话（最近3轮）：\n" +
                "{history}\n" +
                "\n" +
                "当前游客问题：\n" +
                "{question}\n" +
                "\n" +
                "请只输出分类标签，不要输出任何解释：\n" +
                "route_plan 或 spot_question 或 complex_other")
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel("qwen3.5-flash") // 用Flash，速度最快、成本最低
                        .withTemperature(0.1)        // 温度极低，完全不随机，保证分类100%稳定
                        .withTopP(0.8)               // Top P稍低，只选最确定的分类
                        .withMaxToken(32)           // 只输出JSON分类，不需要长文本
                        .withResponseFormat(DashScopeResponseFormat.builder().type(DashScopeResponseFormat.Type.JSON_OBJECT).build())  // 强制JSON输出（Plus/Flash都支持）
                        .build()).build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // TODO: 实现意图分类逻辑
        // 从state中获取question和history
        // 调用chatClient进行分类
        // 将分类结果放回state
        return new HashMap<>();
    }
}
