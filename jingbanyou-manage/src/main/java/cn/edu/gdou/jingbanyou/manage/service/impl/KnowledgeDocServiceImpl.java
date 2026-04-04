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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 知识库文档 Service 实现类
 *
 * @author jingbanyou
 */
@Slf4j
@Service
public class KnowledgeDocServiceImpl extends ServiceImpl<KnowledgeDocMapper, KnowledgeDoc> implements IKnowledgeDocService
{
    @Autowired
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void vectorizeDoc(Long docId)
    {
        KnowledgeDoc doc = getById(docId);
        if (doc == null)
        {
            throw new RuntimeException("文档不存在: " + docId);
        }
        if (doc.getDocContent() == null || doc.getDocContent().isEmpty())
        {
            throw new RuntimeException("文档内容为空: " + docId);
        }

        log.info("开始切分文档: id={}, title={}", docId, doc.getDocTitle());

        // 删除旧的chunk记录
        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocId, docId));

        // 使用Spring AI的TokenTextSplitter切分文档
        TokenTextSplitter splitter = new TokenTextSplitter();
        Document document = new Document(doc.getDocContent());
        List<Document> splitDocs = splitter.split(document);

        // 保存chunk记录
        for (int i = 0; i < splitDocs.size(); i++)
        {
            String text = splitDocs.get(i).getText();
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocId(docId);
            chunk.setChunkIndex(i);
            chunk.setChunkContent(text);
            chunk.setChunkTokens(text.length() / 2);
            chunk.setCreateTime(new Date());
            knowledgeChunkMapper.insert(chunk);
        }

        // 更新文档状态
        doc.setChunkCount(splitDocs.size());
        doc.setVectorized(1);
        doc.setUpdateTime(new Date());
        updateById(doc);

        log.info("文档切分完成: id={}, chunks={}", docId, splitDocs.size());
    }
}
