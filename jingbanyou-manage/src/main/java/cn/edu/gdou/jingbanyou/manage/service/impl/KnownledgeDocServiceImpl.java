package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.manage.entity.KnowledgeDoc;
import cn.edu.gdou.jingbanyou.manage.mapper.KnowledgeDocMapper;
import cn.edu.gdou.jingbanyou.manage.service.IKnownledgeDocService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 知识库文档 Service 实现类
 */
@Service
public class KnownledgeDocServiceImpl extends ServiceImpl<KnowledgeDocMapper, KnowledgeDoc> implements IKnownledgeDocService {
}
