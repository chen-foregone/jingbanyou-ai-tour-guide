package cn.edu.gdou.jingbanyou.tourist.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 节点配置属性类
 * 从 application.yml 中读取 jingbanyou.ai.nodes 配置
 * 
 * @author JingbanYou Team
 * @date 2026-04-07
 */
@Data
@Component
@ConfigurationProperties(prefix = "jingbanyou.ai")
public class AiNodeProperties {
    
    /**
     * 所有节点的配置，key 为节点名称（如 distinguish、route-param-extractor）
     */
    private Map<String, NodeConfig> nodes;
    
    /**
     * 单个节点的配置
     */
    @Data
    public static class NodeConfig {
        /**
         * 提示词模板（支持 {variable} 占位符）
         */
        private String prompt;
        
        /**
         * 模型配置
         */
        private ModelConfig model;
    }
    
    /**
     * 模型配置
     */
    @Data
    public static class ModelConfig {
        /**
         * 模型名称（如 qwen3.5-flash、qwen3.6-plus）
         */
        private String name;
        
        /**
         * 温度参数（0-1，越高越随机）
         */
        private Double temperature;
        
        /**
         * Top P 参数（0-1，控制采样范围）
         */
        private Double topP;
        
        /**
         * 最大 Token 数
         */
        private Integer maxTokens;
        
        /**
         * 响应格式（JSON_OBJECT 或 null）
         */
        private String responseFormat;
    }
}
