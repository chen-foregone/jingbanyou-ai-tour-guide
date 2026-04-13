package cn.edu.gdou.jingbanyou.manage.tool;

import cn.edu.gdou.jingbanyou.manage.entity.KnowledgeChunk;
import cn.edu.gdou.jingbanyou.manage.entity.KnowledgeDoc;
import cn.edu.gdou.jingbanyou.manage.mapper.KnowledgeChunkMapper;
import cn.edu.gdou.jingbanyou.manage.mapper.KnowledgeDocMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 景区知识文档 RAG 检索工具
 * AI 可通过此工具在景区知识库文档中检索相关内容（景点介绍、历史文化、游览攻略等）
 */
@Slf4j
@Component
public class ScenicKnowledgeDocRagTool {

    @Autowired
    private KnowledgeDocMapper knowledgeDocMapper;

    @Autowired
    private KnowledgeChunkMapper knowledgeChunkMapper;

    /**
     * 检索景区知识文档，返回与问题最相关的文档内容片段
     * 使用关键词匹配方式检索 chunks，支持景点介绍、历史背景、游览攻略等知识
     *
     * @param scenicId 景区 ID
     * @param query    游客查询内容（如景点名称、历史事件、游览建议等）
     * @return 检索到的文档内容片段，最多返回 3 条
     */
    @Tool(name = "knowledge_doc_search", description = """
        在景区知识文档库中检索相关内容。
        当游客询问景点的详细介绍、历史背景、文化渊源、游览攻略、最佳游览季节、注意事项等知识性问题时使用。
        返回检索到的文档片段供 AI 参考回答。
        """)
    public List<DocChunkResult> searchKnowledgeDoc(
            @ToolParam(description = "景区 ID", required = true) Long scenicId,
            @ToolParam(description = "游客的查询内容，如'灵山大佛的历史'、'游览攻略推荐'、'最佳路线建议'", required = true) String query) {
        log.debug("[KnowledgeDoc-RAG] scenicId={}, query={}", scenicId, query);

        List<DocChunkResult> results = new ArrayList<>();

        // 1. 先找该景区下已向量化且启用状态的文档
        List<KnowledgeDoc> docs = knowledgeDocMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDoc>()
                        .eq(KnowledgeDoc::getScenicId, scenicId)
                        .eq(KnowledgeDoc::getStatus, 1)
                        .eq(KnowledgeDoc::getVectorized, 1)
                        .last("LIMIT 10")
        );

        for (KnowledgeDoc doc : docs) {
            // 2. 在 chunks 中做关键词匹配
            List<KnowledgeChunk> matchedChunks = knowledgeChunkMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeChunk>()
                            .eq(KnowledgeChunk::getDocId, doc.getId())
                            .like(KnowledgeChunk::getChunkContent, query)
                            .last("LIMIT 3")
            );

            for (KnowledgeChunk chunk : matchedChunks) {
                results.add(new DocChunkResult(
                        doc.getDocTitle(),
                        doc.getDocType(),
                        chunk.getChunkContent()
                ));
            }
        }

        if (results.isEmpty()) {
            // fallback: 全文检索原始文档内容
            List<KnowledgeDoc> fallbackDocs = knowledgeDocMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeDoc>()
                            .eq(KnowledgeDoc::getScenicId, scenicId)
                            .eq(KnowledgeDoc::getStatus, 1)
                            .like(KnowledgeDoc::getDocContent, query)
                            .last("LIMIT 2")
            );
            for (KnowledgeDoc doc : fallbackDocs) {
                results.add(new DocChunkResult(
                        doc.getDocTitle(),
                        doc.getDocType(),
                        doc.getDocContent().length() > 500
                                ? doc.getDocContent().substring(0, 500) + "..."
                                : doc.getDocContent()
                ));
            }
        }

        log.debug("[KnowledgeDoc-RAG] found {} results", results.size());
        return results;
    }

    public static class DocChunkResult {
        public final String docTitle;
        public final String docType;
        public final String content;

        public DocChunkResult(String docTitle, String docType, String content) {
            this.docTitle = docTitle;
            this.docType = docType;
            this.content = content;
        }
    }
}
