package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.KnowledgeChunk;
import cn.edu.gdou.jingbanyou.manage.entity.KnowledgeDoc;
import cn.edu.gdou.jingbanyou.manage.mapper.KnowledgeChunkMapper;
import cn.edu.gdou.jingbanyou.manage.mapper.KnowledgeDocMapper;
import cn.edu.gdou.jingbanyou.manage.service.IKnowledgeDocService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库文档 Service 实现类
 *
 * @author jingbanyou
 */
@Slf4j
@Service
public class KnowledgeDocServiceImpl extends ServiceImpl<KnowledgeDocMapper, KnowledgeDoc> implements IKnowledgeDocService {

    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final VectorStore knowledgeVectorStore;
    private final ObjectMapper objectMapper;

    /** Embedding 模型版本（从配置读取） */
    @Value("${spring.ai.dashscope.embedding.options.model:text-embedding-v2}")
    private String embeddingModel;

    /** 讲解词(docType=1) chunk size */
    @Value("${jingbanyou.knowledge-doc.chunk.type1.chunk-size:400}")
    private int chunkSize1;

    /** 讲解词(docType=1) overlap */
    @Value("${jingbanyou.knowledge-doc.chunk.type1.overlap:50}")
    private int overlap1;

    /** 文史资料(docType=2) chunk size */
    @Value("${jingbanyou.knowledge-doc.chunk.type2.chunk-size:600}")
    private int chunkSize2;

    /** 文史资料(docType=2) overlap */
    @Value("${jingbanyou.knowledge-doc.chunk.type2.overlap:100}")
    private int overlap2;

    /** 攻略(docType=3) chunk size */
    @Value("${jingbanyou.knowledge-doc.chunk.type3.chunk-size:800}")
    private int chunkSize3;

    /** 攻略(docType=3) overlap */
    @Value("${jingbanyou.knowledge-doc.chunk.type3.overlap:100}")
    private int overlap3;

    /** 公告(docType=4) chunk size */
    @Value("${jingbanyou.knowledge-doc.chunk.type4.chunk-size:300}")
    private int chunkSize4;

    /** 公告(docType=4) overlap */
    @Value("${jingbanyou.knowledge-doc.chunk.type4.overlap:30}")
    private int overlap4;

    public KnowledgeDocServiceImpl(KnowledgeChunkMapper knowledgeChunkMapper,
                                  @Qualifier("knowledgeVectorStore") VectorStore knowledgeVectorStore,
                                  ObjectMapper objectMapper) {
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeVectorStore = knowledgeVectorStore;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据文档类型获取对应的 TokenTextSplitter
     *
     * @param docType 文档类型
     * @return 配置好的 TokenTextSplitter
     */
    private TokenTextSplitter getSplitterByDocType(String docType) {
        int chunkSize;
        switch (docType != null ? docType : "1") {
            case "2":
                chunkSize = chunkSize2;
                break;
            case "3":
                chunkSize = chunkSize3;
                break;
            case "4":
                chunkSize = chunkSize4;
                break;
            default:
                chunkSize = chunkSize1;
        }
        return TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(10)
                .withMaxNumChunks(100)
                .build();
    }

    /**
     * 文档向量化（文本切分 + chunk管理）
     *
     * @param docId 文档ID
     */
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

        // 3. 切分文档（根据 docType 使用不同 chunk 参数）
        TokenTextSplitter splitter = getSplitterByDocType(doc.getDocType());
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
            // 保存元数据到 MySQL（与 Redis 向量库保持一致）
            try {
                chunk.setMetadata(objectMapper.writeValueAsString(metadata));
            } catch (Exception e) {
                log.warn("元数据序列化失败，跳过: {}", e.getMessage());
            }
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

    /**
     * 删除文档（同步清理 Redis 向量 + MySQL chunk 记录）
     *
     * @param docId 文档ID
     */
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
     *
     * @param docId 文档ID
     */
    private void deleteChunksAndVectors(Long docId) {
        // 查出旧 vectorId 列表
        List<KnowledgeChunk> oldChunks = knowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocId, docId));
        List<String> oldVectorIds = oldChunks.stream()
                .map(KnowledgeChunk::getVectorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 删 MySQL chunk 记录
        knowledgeChunkMapper.delete(
                new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocId, docId));

        // 删 Redis 向量
        if (!oldVectorIds.isEmpty()) {
            knowledgeVectorStore.delete(oldVectorIds);
            log.info("已删除 Redis 中的旧向量: {} 个", oldVectorIds.size());
        }
    }

    /**
     * 批量向量化（只处理 vectorized=0 的文档）
     *
     * 语义说明：尽力处理（best-effort），允许部分失败。失败的文档 ID 会记录到日志，
     * 可通过 batchVectorizeByScenic 重新处理。单个文档的 vectorizeDoc 方法有独立事务保障。
     *
     * @return 成功向量化的数量
     */
    @Override
    public int batchVectorize() {
        int totalSuccess = 0;
        int currentPage = 1;
        final int pageSize = 100;

        while (true) {
            Page<KnowledgeDoc> page = new Page<>(currentPage, pageSize);
            page = page(page, new LambdaQueryWrapper<KnowledgeDoc>()
                    .eq(KnowledgeDoc::getVectorized, 0)
                    .eq(KnowledgeDoc::getStatus, 1));

            if (page.getRecords() == null || page.getRecords().isEmpty()) {
                break;
            }

            for (KnowledgeDoc doc : page.getRecords()) {
                try {
                    vectorizeDoc(doc.getId());
                    totalSuccess++;
                } catch (Exception e) {
                    log.error("向量化失败: docId={}", doc.getId(), e);
                }
            }

            if (currentPage >= page.getPages()) {
                break;
            }
            currentPage++;
        }

        log.info("批量向量化完成: 共处理 {} 条", totalSuccess);
        return totalSuccess;
    }

    /**
     * 按景区 ID 批量向量化（只处理指定景区下 vectorized=0 的文档）
     *
     * 语义说明：尽力处理（best-effort），允许部分失败。失败的文档 ID 会记录到日志。
     *
     * @param scenicId 景区ID
     * @return 成功向量化的数量
     */
    @Override
    public int batchVectorizeByScenic(Long scenicId) {
        int totalSuccess = 0;
        int currentPage = 1;
        final int pageSize = 100;

        while (true) {
            Page<KnowledgeDoc> page = new Page<>(currentPage, pageSize);
            page = page(page, new LambdaQueryWrapper<KnowledgeDoc>()
                    .eq(KnowledgeDoc::getScenicId, scenicId)
                    .eq(KnowledgeDoc::getVectorized, 0)
                    .eq(KnowledgeDoc::getStatus, 1));

            if (page.getRecords() == null || page.getRecords().isEmpty()) {
                break;
            }

            for (KnowledgeDoc doc : page.getRecords()) {
                try {
                    vectorizeDoc(doc.getId());
                    totalSuccess++;
                } catch (Exception e) {
                    log.error("向量化失败: docId={}", doc.getId(), e);
                }
            }

            if (currentPage >= page.getPages()) {
                break;
            }
            currentPage++;
        }

        log.info("景区 {} 批量向量化完成: 共处理 {} 条", scenicId, totalSuccess);
        return totalSuccess;
    }
}
