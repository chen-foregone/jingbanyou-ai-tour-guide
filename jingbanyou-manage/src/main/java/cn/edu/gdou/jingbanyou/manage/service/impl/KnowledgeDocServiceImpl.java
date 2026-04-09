package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.KnowledgeChunk;
import cn.edu.gdou.jingbanyou.manage.entity.KnowledgeDoc;
import cn.edu.gdou.jingbanyou.manage.mapper.KnowledgeChunkMapper;
import cn.edu.gdou.jingbanyou.manage.mapper.KnowledgeDocMapper;
import cn.edu.gdou.jingbanyou.manage.service.IKnowledgeDocService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 知识库文档 Service 实现类
 *
 * @author jingbanyou
 */
@Slf4j
@Service
public class KnowledgeDocServiceImpl extends ServiceImpl<KnowledgeDocMapper, KnowledgeDoc> implements IKnowledgeDocService {

    @Autowired
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Autowired
    private VectorStore knowledgeVectorStore;

    /** Embedding 模型版本（从配置读取） */
    @Value("${spring.ai.dashscope.embedding.options.model:text-embedding-v2}")
    private String embeddingModel;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void vectorizeDoc(Long docId) {
        KnowledgeDoc doc = getById(docId);
        if (doc == null) {
            throw new RuntimeException("文档不存在: " + docId);
        }
        if (doc.getDocContent() == null || doc.getDocContent().isBlank()) {
            throw new RuntimeException("文档内容为空: " + docId);
        }

        log.info("开始向量化文档: id={}, title={}", docId, doc.getDocTitle());

        // 1-2. 清理旧 chunk 和旧向量（抽取公共方法复用）
        deleteChunksAndVectors(docId);

        // 3. 切分文档
        TokenTextSplitter splitter = new TokenTextSplitter();
        Document sourceDoc = new Document(doc.getDocContent());
        List<Document> splitDocs = splitter.split(sourceDoc);

        // 5. 逐条写入 Redis 向量库并保存 chunk 记录
        for (int i = 0; i < splitDocs.size(); i++) {
            Document splitDoc = splitDocs.get(i);
            String text = splitDoc.getText();

            // 构建元数据（供检索时过滤使用）
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("docId", docId);
            metadata.put("scenicId", doc.getScenicId());
            metadata.put("docTitle", doc.getDocTitle());
            metadata.put("chunkIndex", i);
            splitDoc.getMetadata().putAll(metadata);

            // 写入 Redis 向量库，返回的 Document 包含 vectorId
            knowledgeVectorStore.add(List.of(splitDoc));

            // 保存 chunk 记录
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocId(docId);
            chunk.setChunkIndex(i);
            chunk.setChunkContent(text);
            chunk.setChunkTokens(text.length() / 2);
            //切分后的docId为vectorId
            chunk.setVectorId(splitDoc.getId());
            chunk.setEmbeddingVersion(embeddingModel);
            chunk.setCreateTime(new Date());
            knowledgeChunkMapper.insert(chunk);

            log.debug("Chunk {} 写入完成: vectorId={}", i, splitDoc.getId());
        }

        // 更新文档状态
        doc.setChunkCount(splitDocs.size());
        doc.setVectorized(1);
        doc.setEmbeddingModel(embeddingModel);
        doc.setUpdateTime(new Date());
        updateById(doc);

        log.info("文档向量化完成: id={}, chunks={}", docId, splitDocs.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDocWithVector(Long docId) {
        KnowledgeDoc doc = getById(docId);
        if (doc == null) {
            throw new RuntimeException("文档不存在: " + docId);
        }

        log.info("开始删除文档及其向量: id={}, title={}", docId, doc.getDocTitle());

        // 1. 清理 chunk 表 + Redis 向量
        deleteChunksAndVectors(docId);

        // 2. 删除文档主记录
        removeById(docId);

        log.info("文档删除完成: id={}", docId);
    }

    /**
     * 清理指定文档的 chunk 记录和 Redis 向量（向量化和删除时共用）
     */
    private void deleteChunksAndVectors(Long docId) {
        // 查出旧 vectorId 列表
        List<KnowledgeChunk> oldChunks = knowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocId, docId));
        List<String> oldVectorIds = oldChunks.stream()
                .map(KnowledgeChunk::getVectorId)
                .filter(Objects::nonNull)
                .toList();

        // 删 MySQL chunk 记录
        knowledgeChunkMapper.delete(
                new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocId, docId));

        // 删 Redis 向量
        if (!oldVectorIds.isEmpty()) {
            knowledgeVectorStore.delete(oldVectorIds);
            log.info("已删除 Redis 中的旧向量: {} 个", oldVectorIds.size());
        }
    }

    @Override
    public int batchVectorize() {
        List<KnowledgeDoc> pending = list(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getVectorized, 0)
                .eq(KnowledgeDoc::getStatus, 1));
        int count = 0;
        for (KnowledgeDoc doc : pending) {
            try {
                vectorizeDoc(doc.getId());
                count++;
            } catch (Exception e) {
                log.error("向量化失败: docId={}", doc.getId(), e);
            }
        }
        log.info("批量向量化完成: 共处理 {} 条", count);
        return count;
    }

    @Override
    public int batchVectorizeByScenic(Long scenicId) {
        List<KnowledgeDoc> pending = list(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getScenicId, scenicId)
                .eq(KnowledgeDoc::getVectorized, 0)
                .eq(KnowledgeDoc::getStatus, 1));
        int count = 0;
        for (KnowledgeDoc doc : pending) {
            try {
                vectorizeDoc(doc.getId());
                count++;
            } catch (Exception e) {
                log.error("向量化失败: docId={}", doc.getId(), e);
            }
        }
        log.info("景区 {} 批量向量化完成: 共处理 {} 条", scenicId, count);
        return count;
    }
}
