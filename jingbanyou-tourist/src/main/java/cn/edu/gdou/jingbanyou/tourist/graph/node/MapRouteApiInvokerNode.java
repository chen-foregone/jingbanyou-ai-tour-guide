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
 * 路线润色节点（导航话术生成）
 * 任务：把结构化路线数据（经纬度、距离、时间）转换成自然、友好、游客易懂的导航话术
 */
@Component
public class MapRouteApiInvokerNode implements NodeAction {

    @Autowired
    private ChatClient.Builder builder;
    private ChatClient chatClient;

    public MapRouteApiInvokerNode(ChatClient.Builder builder) {
        this.builder = builder;
        chatClient = builder.defaultSystem("你是一个熟悉本景区的自然风景区导游，语气亲切自然。\n" +
                "\n" +
                "请将以下结构化的路线数据转换为流畅、口语化的导航话术，让游客听起来像在和真人导游对话。\n" +
                "\n" +
                "要求：\n" +
                "- 使用\"您\"称呼游客\n" +
                "- 路线描述自然流畅，结合景区特色适当加入1句景色描述\n" +
                "- 重要转弯或标志物要清晰说明\n" +
                "- 不超过150字\n" +
                "\n" +
                "路线数据：\n" +
                "{route_data}")
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel("qwen3.6-plus")  // 用Plus，生成的话术更流畅、更有人情味
                        .withTemperature(0.7)        // 温度适中，既保证路线准确，又让话术自然不生硬
                        .withTopP(0.9)               // Top P稍高，允许一定的表达多样性
                        .withMaxToken(512)          // 生成完整的导航话术，需要稍长的文本
                        .build()).build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // TODO: 实现地图路线API调用逻辑
        return new HashMap<>();
    }
}
