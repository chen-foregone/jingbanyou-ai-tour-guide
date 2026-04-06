package cn.edu.gdou.jingbanyou.tourist.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphConfiguration{

    @Bean
    public CompiledGraph compileGraph() throws GraphStateException {
        StateGraph stateGraph = new StateGraph();
        CompiledGraph compile = stateGraph.compile();
        return compile;
    }
}
5