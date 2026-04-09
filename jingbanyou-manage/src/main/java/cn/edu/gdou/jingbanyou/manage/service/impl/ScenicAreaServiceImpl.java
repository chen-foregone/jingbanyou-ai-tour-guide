package cn.edu.gdou.jingbanyou.manage.service.impl;

import cn.edu.gdou.jingbanyou.common.utils.poi.ExcelUtil;
import cn.edu.gdou.jingbanyou.manage.dto.ScenicAreaExcelDTO;
import cn.edu.gdou.jingbanyou.manage.entity.KnowledgeDoc;
import cn.edu.gdou.jingbanyou.manage.entity.ScenicArea;
import cn.edu.gdou.jingbanyou.manage.mapper.KnowledgeDocMapper;
import cn.edu.gdou.jingbanyou.manage.mapper.ScenicAreaMapper;
import cn.edu.gdou.jingbanyou.manage.service.IScenicAreaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 景区基础信息 Service 实现类
 */
@Slf4j
@Service
public class ScenicAreaServiceImpl extends ServiceImpl<ScenicAreaMapper, ScenicArea> implements IScenicAreaService {

    @Autowired
    private KnowledgeDocMapper knowledgeDocMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importFromExcel(MultipartFile file, Long creator) {
        // 1. 读取 Excel
        ExcelUtil<ScenicAreaExcelDTO> util = new ExcelUtil<>(ScenicAreaExcelDTO.class);
        List<ScenicAreaExcelDTO> rows;
        try {
            rows = util.importExcel(file.getInputStream());
        } catch (Exception e) {
            throw new RuntimeException("Excel 解析失败: " + e.getMessage(), e);
        }

        if (rows == null || rows.isEmpty()) {
            return "Excel 文件为空或无可用数据";
        }

        // 2. 按景区名称去重（取每组第一条）
        Map<String, ScenicAreaExcelDTO> uniqueMap = rows.stream()
                .filter(r -> r.getAttractionName() != null && !r.getAttractionName().isBlank())
                .collect(Collectors.toMap(
                        ScenicAreaExcelDTO::getAttractionName,
                        r -> r,
                        (existing, replacement) -> existing  // 重复时保留第一条
                ));

        log.info("Excel 总行数={}，去重后景区数={}", rows.size(), uniqueMap.size());

        // 3. 查出已存在的景区名称集合
        List<String> existNames = list(new LambdaQueryWrapper<ScenicArea>()
                        .select(ScenicArea::getScenicName)
                        .in(ScenicArea::getScenicName, uniqueMap.keySet()))
                .stream()
                .map(ScenicArea::getScenicName)
                .collect(Collectors.toList());

        Set<String> existNameSet = new HashSet<>(existNames);

        int insertedScenic = 0;
        int skippedScenic = 0;
        int insertedDoc = 0;

        // 4. 逐条写入
        for (ScenicAreaExcelDTO dto : uniqueMap.values()) {
            String name = dto.getAttractionName().trim();

            // 景区基础信息（只写入不在库中的）
            if (!existNameSet.contains(name)) {
                ScenicArea scenic = new ScenicArea();
                scenic.setScenicName(name);
                scenic.setScenicDesc(dto.getAttractionType());  // 类型放 scenic_desc 字段
                scenic.setStatus(1);
                scenic.setSort(0);
                scenic.setCreateTime(new Date());
                scenic.setUpdateTime(new Date());
                save(scenic);
                insertedScenic++;
            } else {
                skippedScenic++;
            }

            // 查找刚入库（或已存在）的景区 ID
            ScenicArea scenicInDb = getOne(new LambdaQueryWrapper<ScenicArea>()
                    .eq(ScenicArea::getScenicName, name));
            if (scenicInDb == null) {
                continue;
            }

            // 景区介绍写入知识库文档（每次导入都更新）
            if (dto.getAttractionContent() != null && !dto.getAttractionContent().isBlank()) {
                // 先删后插（同一景区只保留最新一份）
                knowledgeDocMapper.delete(new LambdaQueryWrapper<KnowledgeDoc>()
                        .eq(KnowledgeDoc::getScenicId, scenicInDb.getId())
                        .eq(KnowledgeDoc::getDocType, "文史资料"));

                KnowledgeDoc doc = new KnowledgeDoc();
                doc.setScenicId(scenicInDb.getId());
                doc.setDocTitle(name + " - 景区介绍");
                doc.setDocType("文史资料");
                doc.setDocContent(dto.getAttractionContent());
                doc.setWordCount(dto.getAttractionContent().length());
                doc.setVectorized(0);  // 待后续向量化
                doc.setCreator(creator);
                doc.setStatus(1);
                doc.setViewCount(0);
                doc.setCreateTime(new Date());
                doc.setUpdateTime(new Date());
                knowledgeDocMapper.insert(doc);
                insertedDoc++;
            }
        }

        log.info("景区导入完成：新增景区={}，跳过={}，写入知识文档={}", insertedScenic, skippedScenic, insertedDoc);
        return String.format("导入完成。新增景区 %d 个，跳过（已存在）%d 个，写入知识文档 %d 份。",
                insertedScenic, skippedScenic, insertedDoc);
    }
}
