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
    void vectorizeDoc(Long docId);

    /**
     * 批量向量化（只处理 vectorized=0 的文档）
     *
     * @return 成功向量化的数量
     */
    int batchVectorize();

    /**
     * 按景区 ID 批量向量化（只处理指定景区下 vectorized=0 的文档）
     *
     * @param scenicId 景区ID
     * @return 成功向量化的数量
     */
    int batchVectorizeByScenic(Long scenicId);
}
