package cn.edu.gdou.jingbanyou.manage.service;

import cn.edu.gdou.jingbanyou.manage.entity.KnowledgeDoc;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 知识库文档 Service 接口
 *
 * @author jingbanyou
 */
public interface IKnowledgeDocService extends IService<KnowledgeDoc>
{
    /**
     * 文档向量化（文本切分 + chunk管理）
     *
     * @param docId 文档ID
     */
    public void vectorizeDoc(Long docId);
}
