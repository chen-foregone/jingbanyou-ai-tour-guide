package cn.edu.gdou.jingbanyou.manage.config;

import cn.edu.gdou.jingbanyou.manage.service.IFaqService;
import cn.edu.gdou.jingbanyou.manage.tool.ScenicFaqRagTool;
import cn.edu.gdou.jingbanyou.manage.tool.ScenicKnowledgeDocRagTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG Tool 配置类
 * 将 FAQ 和知识文档检索工具注册为 Spring Bean，供 AI Tool Calling 使用
 */
@Configuration
public class RagToolConfig {

    @Bean
    public ScenicFaqRagTool scenicFaqRagTool(IFaqService faqService) {
        ScenicFaqRagTool tool = new ScenicFaqRagTool();
        tool.setFaqService(faqService);
        return tool;
    }

    @Bean
    public ScenicKnowledgeDocRagTool scenicKnowledgeDocRagTool() {
        return new ScenicKnowledgeDocRagTool();
    }
}
