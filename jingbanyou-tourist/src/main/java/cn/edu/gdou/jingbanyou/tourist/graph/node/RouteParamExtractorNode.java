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
 * 参数提取节点（路线规划用）
 * 任务：从用户问题里提取「起点、终点」，结构化输出，要求精准、无遗漏
 */
@Component
public class RouteParamExtractorNode implements NodeAction {

    @Autowired
    private ChatClient.Builder builder;
    private ChatClient chatClient;

    public RouteParamExtractorNode(ChatClient.Builder builder) {
        this.builder = builder;
        chatClient = builder.defaultSystem("你是一个参数提取助手。\n" +
                "\n" +
                "游客想要规划景区内的游览路线，请从游客的问题中提取以下参数：\n" +
                "- start：出发地点（景区内的景点名称或\"入口\"\"停车场\"等位置）\n" +
                "- end：目的地（景区内的景点名称）\n" +
                "\n" +
                "历史对话（最近3轮）：\n" +
                "{history}\n" +
                "\n" +
                "当前游客问题：\n" +
                "{question}\n" +
                "\n" +
                "请以 JSON 格式输出，缺失的参数值设为 null：\n" +
                "{\n" +
                "  \"start\": \"出发地点或null\",\n" +
                "  \"end\": \"目的地或null\"\n" +
                "}\n" +
                "\n" +
                "只输出 JSON，不要输出其他内容。")
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel("qwen3.5-flash") // Flash足够处理简单的槽位提取
                        .withTemperature(0.1)        // 温度极低，保证提取结果稳定，不丢参数
                        .withTopP(0.85)
                        .withMaxToken(64)           // 只放提取的起点、终点，短文本足够
                        .withResponseFormat(DashScopeResponseFormat.builder().type(DashScopeResponseFormat.Type.JSON_OBJECT).build())  // 强制JSON，方便后端直接解析参数
                        .build()).build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // TODO: 实现路线参数提取逻辑
        return new HashMap<>();
    }
}
