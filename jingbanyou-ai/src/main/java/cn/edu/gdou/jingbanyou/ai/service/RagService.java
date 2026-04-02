package cn.edu.gdou.jingbanyou.ai.service;

import java.util.List;

/**
 * RAG 知识检索 Service（AI 中台核心能力）
 * 
 * 功能：从本地知识库检索相关知识，增强大模型回答准确性
 */
public interface RagService {

    /**
     * 检索相关知识
     * @param query 用户问题
     * @param scenicId 景区 ID
     * @param topK 返回数量
     * @return 相关知识片段
     */
    List<String> retrieveKnowledge(String query, Long scenicId, int topK);

    /**
     * 向量化文档
     */
    void embedDocument(Long docId);

    /**
     * 删除向量化文档
     */
    void removeEmbedding(Long docId);
}
