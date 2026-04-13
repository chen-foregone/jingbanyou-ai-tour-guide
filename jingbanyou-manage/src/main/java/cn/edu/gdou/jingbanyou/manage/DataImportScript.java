//package cn.edu.gdou.jingbanyou.manage;
//
//import cn.edu.gdou.jingbanyou.manage.entity.*;
//import cn.edu.gdou.jingbanyou.manage.mapper.*;
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.poi.ss.usermodel.*;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;
//import org.apache.poi.xwpf.usermodel.*;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import org.apache.ibatis.session.ExecutorType;
//import org.apache.ibatis.session.SqlSession;
//import org.apache.ibatis.session.SqlSessionFactory;
//import java.io.FileInputStream;
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.text.SimpleDateFormat;
//import java.util.*;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//import java.util.stream.Collectors;
//
///**
// * 赛题资料包全量导入脚本（增强版）
// *
// * 数据源：
// * 1. 景点景区旅游数据行为分析数据.xlsx  → 游客行为数据
// *    - 独立景点(attraction_name去重) → scenic_spot
// *    - 每行记录 → visitor_interaction（模拟交互）
// *    - 按日聚合 → operation_stats
// *    - 情感/满意度统计 → visitor_analysis
// * 2. 灵山胜境 景点结构化数据集.docx     → 景点结构化数据
// *    - 每行 → scenic_spot（与Excel数据去重合并）
// *    - 每行 → knowledge_doc（讲解词）
// * 3. 灵山胜境：历史、文化、景点特色与个性化游览指南.docx
// *    - 按路线章节 → tour_route
// *    - 按Q&A模式 → faq
// *    - 按景点章节 → scenic_spot（补充）
// *    - 全部内容 → knowledge_doc（文史资料/攻略/公告）
// *
// * 执行方式：启动 Application 后脚本自动运行（仅执行一次）
// */
//@Slf4j
//@Component
//public class DataImportScript implements CommandLineRunner {
//
//    // ==================== 文件路径配置 ====================
//    private static final String EXCEL_BEHAVIOR =
//            "C:\\Users\\c1342\\Downloads\\20260323113204906 (1)\\示范景区公开资料包\\景点景区旅游数据行为分析数据.xlsx";
//    private static final String DOCX_SPOT =
//            "C:\\Users\\c1342\\Downloads\\20260323113204906 (1)\\示范景区公开资料包\\灵山胜境 景点结构化数据集.docx";
//    private static final String DOCX_GUIDE =
//            "C:\\Users\\c1342\\Downloads\\20260323113204906 (1)\\示范景区公开资料包\\灵山胜境：历史、文化、景点特色与个性化游览指南.docx";
//
//    // ==================== 批处理配置 ====================
//    private static final int BATCH_SIZE = 500;
//
//    // ==================== Spring Bean ====================
//    private final ScenicAreaMapper     scenicAreaMapper;
//    private final ScenicSpotMapper     scenicSpotMapper;
//    private final KnowledgeDocMapper    knowledgeDocMapper;
//    private final TourRouteMapper      tourRouteMapper;
//    private final FaqMapper            faqMapper;
//    private final VisitorInteractionMapper visitorInteractionMapper;
//    private final OperationStatsMapper   operationStatsMapper;
//    private final VisitorAnalysisMapper   visitorAnalysisMapper;
//    private final SqlSessionFactory       sqlSessionFactory;
//
//    public DataImportScript(
//            ScenicAreaMapper           scenicAreaMapper,
//            ScenicSpotMapper           scenicSpotMapper,
//            KnowledgeDocMapper         knowledgeDocMapper,
//            TourRouteMapper            tourRouteMapper,
//            FaqMapper                  faqMapper,
//            VisitorInteractionMapper   visitorInteractionMapper,
//            OperationStatsMapper       operationStatsMapper,
//            VisitorAnalysisMapper      visitorAnalysisMapper,
//            SqlSessionFactory          sqlSessionFactory) {
//        this.scenicAreaMapper        = scenicAreaMapper;
//        this.scenicSpotMapper        = scenicSpotMapper;
//        this.knowledgeDocMapper      = knowledgeDocMapper;
//        this.tourRouteMapper         = tourRouteMapper;
//        this.faqMapper               = faqMapper;
//        this.visitorInteractionMapper = visitorInteractionMapper;
//        this.operationStatsMapper    = operationStatsMapper;
//        this.visitorAnalysisMapper   = visitorAnalysisMapper;
//        this.sqlSessionFactory       = sqlSessionFactory;
//    }
//
//    private boolean hasRun = false;
//
//    // 景区ID缓存
//    private Long lingshanId;
//    private Long nianhuaId;
//    private Long zhongguoId;
//
//    // 景点名称 → ScenicSpot (用于去重合并)
//    private final Map<String, ScenicSpot> spotNameCache = new HashMap<>();
//
//    // 已插入的景点名称集合
//    private final Set<String> insertedSpotNames = new HashSet<>();
//
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public void run(String... args) throws Exception {
//        if (hasRun) return;
//        hasRun = true;
//
//        log.info("========== 赛题资料包全量导入开始 ==========");
//        long startTime = System.currentTimeMillis();
//
//        // 初始化景区
//        initScenicAreas();
//
//        // 第1步：Excel游客行为数据
//        importExcelBehavior();
//
//        // 第2步：景点DOCX数据（ScenicSpot + KnowledgeDoc）
//        importSpotDocx();
//
//        // 第3步：导览指南数据（TourRoute + FAQ + 补充ScenicSpot + KnowledgeDoc）
//        importGuideDocx();
//
//        // 第4步：生成游客感受度报告
//        generateVisitorAnalysis();
//
//        long elapsed = System.currentTimeMillis() - startTime;
//        log.info("========== 全量导入完成，耗时 {} ms ==========", elapsed);
//    }
//
//    // ==================== Step 0: 初始化景区 ====================
//    private void initScenicAreas() {
//        lingshanId = ensureScenicArea("灵山胜境", "国家5A级旅游景区 / 佛教文化景区 / 无锡灵山");
//        nianhuaId  = ensureScenicArea("拈花湾小镇", "禅意小镇 / 拈花湾");
//        zhongguoId = ensureScenicArea("中国馆", "2010上海世博会中国国家馆 / 中华艺术宫");
//        log.info("景区初始化完成：灵山={}, 拈花湾={}, 中国馆={}", lingshanId, nianhuaId, zhongguoId);
//    }
//
//    private Long ensureScenicArea(String name, String desc) {
//        ScenicArea existing = scenicAreaMapper.selectOne(
//                new LambdaQueryWrapper<ScenicArea>().eq(ScenicArea::getScenicName, name));
//        if (existing != null) {
//            log.info("景区已存在: {} (id={})", name, existing.getId());
//            return existing.getId();
//        }
//        ScenicArea scenic = new ScenicArea();
//        scenic.setScenicName(name);
//        scenic.setScenicDesc(desc);
//        scenic.setScenicAddress(name + "地址");
//        scenic.setStatus(1);
//        scenic.setSort(0);
//        scenic.setCreateTime(new Date());
//        scenic.setUpdateTime(new Date());
//        scenicAreaMapper.insert(scenic);
//        log.info("景区新增: {} (id={})", name, scenic.getId());
//        return scenic.getId();
//    }
//
//    // ==================== Step 1: Excel游客行为数据 ====================
//    private void importExcelBehavior() throws Exception {
//        log.info("========== 开始导入 Excel 游客行为数据 ==========");
//
//        // 加载已存在的景点到缓存（从DB）
//        loadExistingSpotsToCache();
//
//        AtomicInteger spotCount   = new AtomicInteger(0);
//        AtomicInteger interCount  = new AtomicInteger(0);
//        AtomicInteger statsCount  = new AtomicInteger(0);
//
//        // 按景区+日期分组聚合
//        Map<String, List<ExcelRow>> grouped = new LinkedHashMap<>();
//        List<VisitorInteraction> batchInteractions = new ArrayList<>(BATCH_SIZE);
//        Set<String> dailySpotNames = new HashSet<>();
//
//        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd");
//
//        // 使用 XSSFWorkbook 读取（兼容 WPS 创建的文件）
//        try (FileInputStream fis = new FileInputStream(EXCEL_BEHAVIOR);
//             XSSFWorkbook workbook = (XSSFWorkbook) WorkbookFactory.create(fis)) {
//
//            Sheet sheet = workbook.getSheetAt(0);
//            Iterator<Row> rowIter = sheet.iterator();
//
//            if (!rowIter.hasNext()) {
//                log.warn("Excel为空，跳过");
//                return;
//            }
//
//            // 读取表头
//            Row headerRow = rowIter.next();
//            Map<String, Integer> headerIdx = new HashMap<>();
//            for (Cell cell : headerRow) {
//                String val = getCellString(cell);
//                if (val != null) headerIdx.put(val.trim(), cell.getColumnIndex());
//            }
//
//            int idxTouristId    = headerIdx.getOrDefault("tourist_id", -1);
//            int idxNickname     = headerIdx.getOrDefault("user_nickname", -1);
//            int idxAge          = headerIdx.getOrDefault("age", -1);
//            int idxGender       = headerIdx.getOrDefault("gender", -1);
//            int idxAttrName     = headerIdx.getOrDefault("attraction_name", -1);
//            int idxAttrContent  = headerIdx.getOrDefault("attraction_content", -1);
//            int idxAttrType     = headerIdx.getOrDefault("attraction_type", -1);
//            int idxVisitDate    = headerIdx.getOrDefault("visit_date", -1);
//            int idxStayDuration = headerIdx.getOrDefault("stay_duration", -1);
//            int idxTicketCost   = headerIdx.getOrDefault("ticket_cost", -1);
//            int idxFoodCost     = headerIdx.getOrDefault("food_cost", -1);
//            int idxShopCost     = headerIdx.getOrDefault("shopping_cost", -1);
//            int idxTransCost    = headerIdx.getOrDefault("transport_cost", -1);
//            int idxEntCost      = headerIdx.getOrDefault("entertainment_cost", -1);
//            int idxTotalCost    = headerIdx.getOrDefault("total_cost", -1);
//            int idxGroupSize    = headerIdx.getOrDefault("group_size", -1);
//            int idxSatisfaction = headerIdx.getOrDefault("satisfaction", -1);
//
//            int rowNum = 1;
//            while (rowIter.hasNext()) {
//                Row row = rowIter.next();
//                rowNum++;
//
//                if (rowNum % 10000 == 0) {
//                    log.info("Excel 进度: 行 {} / ~140000", rowNum);
//                }
//
//                String attrName = getCellStringSafe(row, idxAttrName);
//                if (attrName == null || attrName.isBlank()) continue;
//
//                attrName = attrName.trim();
//                Date visitDate   = getCellDateSafe(row, idxVisitDate);
//                String visitDateStr = visitDate != null ? dateFmt.format(visitDate) : "2025-10-01";
//                String key = attrName + "|" + visitDateStr;
//
//                // 读取字段
//                String touristId    = getCellStringSafe(row, idxTouristId);
//                String nickname     = getCellStringSafe(row, idxNickname);
//                Integer age         = getCellIntSafe(row, idxAge);
//                String gender       = getCellStringSafe(row, idxGender);
//                String attrContent  = getCellStringSafe(row, idxAttrContent);
//                String attrType     = getCellStringSafe(row, idxAttrType);
//                Double stayDur     = getCellDoubleSafe(row, idxStayDuration);
//                Double ticketCost   = getCellDoubleSafe(row, idxTicketCost);
//                Double foodCost     = getCellDoubleSafe(row, idxFoodCost);
//                Double shopCost     = getCellDoubleSafe(row, idxShopCost);
//                Double transCost    = getCellDoubleSafe(row, idxTransCost);
//                Double entCost      = getCellDoubleSafe(row, idxEntCost);
//                Double totalCost    = getCellDoubleSafe(row, idxTotalCost);
//                Integer groupSize   = getCellIntSafe(row, idxGroupSize);
//                Integer satisfaction = getCellIntSafe(row, idxSatisfaction);
//
//                // 关联景区
//                Long scenicId = getScenicIdByName(attrName);
//                if (scenicId == null) scenicId = lingshanId;
//
//                // ---- ScenicSpot 去重入库（按景点名去重）----
//                if (!insertedSpotNames.contains(attrName)) {
//                    ScenicSpot spot = new ScenicSpot();
//                    spot.setScenicId(scenicId);
//                    spot.setSpotName(attrName);
//                    spot.setSpotType(normalizeSpotType(attrType));
//                    spot.setSpotDesc(truncate(attrContent, 2000));
//                    spot.setVisitDuration(stayDur != null ? stayDur.intValue() : null);
//                    spot.setStatus(1);
//                    spot.setSort(0);
//                    spot.setCreateTime(new Date());
//                    spot.setUpdateTime(new Date());
//                    scenicSpotMapper.insert(spot);
//                    insertedSpotNames.add(attrName);
//                    spotNameCache.put(attrName, spot);
//                    spotCount.incrementAndGet();
//                }
//
//                // ---- VisitorInteraction 每行入库 ----
//                String emotion = detectEmotion(satisfaction);
//                VisitorInteraction vi = new VisitorInteraction();
//                vi.setScenicId(scenicId);
//                vi.setVisitorId(touristId != null ? touristId : UUID.randomUUID().toString());
//                vi.setSessionId("EXCEL-" + touristId + "-" + visitDateStr);
//                vi.setInteractionType("text");
//                vi.setUserQuestion("游览【" + attrName + "】");
//                vi.setAiAnswer(generateSimulatedAnswer(attrName, attrContent));
//                vi.setEmotionDetected(emotion);
//                vi.setEmotionConfidence(0.85);
//                vi.setIntentType(classifyIntent(attrName, attrContent));
//                vi.setRagUsed(0);
//                vi.setResponseTimeMs(200);
//                vi.setFeedbackScore(satisfaction);
//                vi.setFeedbackText(satisfactionToText(satisfaction));
//                vi.setDeviceType("mobile");
//                vi.setCreateTime(visitDate != null ? visitDate : new Date());
//                batchInteractions.add(vi);
//
//                if (batchInteractions.size() >= BATCH_SIZE) {
//                    batchInsertVisitorInteraction(batchInteractions);
//                    interCount.addAndGet(batchInteractions.size());
//                    batchInteractions.clear();
//                }
//
//                // ---- 按景区+日期分组用于 OperationStats ----
//                String groupKey = scenicId + "|" + visitDateStr;
//                grouped.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(
//                        new ExcelRow(scenicId, visitDateStr, satisfaction,
//                                totalCost != null ? totalCost.floatValue() : 0f,
//                                ticketCost != null ? ticketCost.floatValue() : 0f,
//                                stayDur != null ? stayDur.floatValue() : 0f,
//                                touristId, groupSize != null ? groupSize : 1));
//            }
//
//            // 剩余批次
//            if (!batchInteractions.isEmpty()) {
//                batchInsertVisitorInteraction(batchInteractions);
//                interCount.addAndGet(batchInteractions.size());
//                batchInteractions.clear();
//            }
//        }
//
//        log.info("ScenicSpot 导入: {} 条", spotCount.get());
//        log.info("VisitorInteraction 导入: {} 条", interCount.get());
//
//        // ---- 生成 OperationStats（按景区+日期聚合）----
//        generateOperationStats(grouped, statsCount);
//        log.info("OperationStats 生成: {} 条", statsCount.get());
//
//        log.info("========== Excel 游客行为数据导入完成 ==========");
//    }
//
//    // 加载DB中已存在的景点
//    private void loadExistingSpotsToCache() {
//        List<ScenicSpot> existing = scenicSpotMapper.selectList(null);
//        for (ScenicSpot s : existing) {
//            insertedSpotNames.add(s.getSpotName());
//            spotNameCache.put(s.getSpotName(), s);
//        }
//        log.info("已加载 {} 条已存在景点到缓存", existing.size());
//    }
//
//    // 根据景点名推断景区ID
//    private Long getScenicIdByName(String name) {
//        if (name == null) return lingshanId;
//        String n = name.trim();
//        if (n.contains("拈花") || n.contains("禅意")) return nianhuaId;
//        if (n.contains("中国馆") || n.contains("世博")) return zhongguoId;
//        if (n.contains("灵山")) return lingshanId;
//        return lingshanId;
//    }
//
//    // 规范化景点类型
//    private String normalizeSpotType(String type) {
//        if (type == null) return "人文";
//        String t = type.trim();
//        if (t.contains("历史") || t.contains("文化")) return "历史";
//        if (t.contains("自然") || t.contains("山水") || t.contains("生态")) return "自然";
//        if (t.contains("亲子") || t.contains("娱乐") || t.contains("演艺")) return "娱乐";
//        return "人文";
//    }
//
//    // 根据满意度推断情感
//    private String detectEmotion(Integer satisfaction) {
//        if (satisfaction == null) return "neutral";
//        if (satisfaction >= 4) return "positive";
//        if (satisfaction <= 2) return "negative";
//        return "neutral";
//    }
//
//    // 根据景点内容推断意图
//    private String classifyIntent(String name, String content) {
//        if (name == null && content == null) return "inquiry";
//        String all = (name != null ? name : "") + " " + (content != null ? content : "");
//        if (all.contains("投诉") || all.contains("问题") || all.contains("不满")) return "complaint";
//        if (all.contains("建议") || all.contains("希望") || all.contains("改进")) return "suggestion";
//        if (all.contains("好") || all.contains("棒") || all.contains("赞") || all.contains("推荐")) return "praise";
//        return "inquiry";
//    }
//
//    private String satisfactionToText(Integer satisfaction) {
//        if (satisfaction == null) return "";
//        return switch (satisfaction) {
//            case 5 -> "非常满意";
//            case 4 -> "满意";
//            case 3 -> "一般";
//            case 2 -> "不满意";
//            case 1 -> "非常不满意";
//            default -> "";
//        };
//    }
//
//    private String generateSimulatedAnswer(String name, String content) {
//        if (content == null || content.isBlank()) {
//            return "欢迎来到" + name + "，祝您游玩愉快！";
//        }
//        // 取前100字作为简介
//        String brief = content.replaceAll("\\s+", " ").trim();
//        if (brief.length() > 100) brief = brief.substring(0, 100) + "...";
//        return "关于【" + name + "】的介绍：" + brief;
//    }
//
//    // 生成每日运营统计
//    private void generateOperationStats(Map<String, List<ExcelRow>> grouped, AtomicInteger statsCount) {
//        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
//        List<OperationStats> batchStats = new ArrayList<>(BATCH_SIZE);
//        for (Map.Entry<String, List<ExcelRow>> entry : grouped.entrySet()) {
//            String[] parts = entry.getKey().split("\\|");
//            Long scenicId = Long.parseLong(parts[0]);
//            String dateStr = parts[1];
//
//            List<ExcelRow> rows = entry.getValue();
//            int total = rows.size();
//
//            // 独立访客
//            Set<String> uniqueVisitors = rows.stream()
//                    .map(r -> r.touristId)
//                    .filter(Objects::nonNull)
//                    .collect(Collectors.toSet());
//
//            // 平均满意度
//            double avgSat = rows.stream()
//                    .filter(r -> r.satisfaction != null)
//                    .mapToInt(r -> r.satisfaction)
//                    .average().orElse(0);
//
//            try {
//                OperationStats stats = OperationStats.builder()
//                        .scenicId(scenicId)
//                        .statDate(fmt.parse(dateStr))
//                        .totalInteractions(total)
//                        .uniqueVisitors(uniqueVisitors.size())
//                        .avgSatisfaction(BigDecimal.valueOf(avgSat).setScale(2, RoundingMode.HALF_UP))
//                        .textCount(total)
//                        .voiceCount(0)
//                        .avgResponseTimeMs(200)
//                        .build();
//                stats.setCreateTime(new Date());
//                batchStats.add(stats);
//                statsCount.incrementAndGet();
//                if (batchStats.size() >= BATCH_SIZE) {
//                    batchInsertOperationStats(batchStats);
//                    batchStats.clear();
//                }
//            } catch (Exception e) {
//                log.warn("OperationStats 插入失败: {}", dateStr, e);
//            }
//        }
//        // 剩余批次
//        if (!batchStats.isEmpty()) {
//            batchInsertOperationStats(batchStats);
//        }
//    }
//
//    // ==================== Step 2: 景点结构化数据集 DOCX ====================
//    private void importSpotDocx() throws Exception {
//        log.info("========== 开始导入景点结构化数据集 DOCX ==========");
//        int spotCount = 0;
//        int docCount  = 0;
//
//        try (FileInputStream fis = new FileInputStream(DOCX_SPOT);
//             XWPFDocument document = new XWPFDocument(fis)) {
//
//            List<XWPFTable> tables = document.getTables();
//
//            for (XWPFTable table : tables) {
//                List<XWPFTableRow> rows = table.getRows();
//                if (rows.size() < 2) continue;
//
//                // 景区名在第一行数据中（表头行的第一个单元格是"景区名称"，需跳过）
//                XWPFTableRow firstDataRow = rows.get(1);
//                String scenicName = getCellTextSafe(firstDataRow, 0).trim();
//
//                Long scenicId;
//                if (scenicName.contains("灵山")) {
//                    scenicId = lingshanId;
//                } else if (scenicName.contains("拈花")) {
//                    scenicId = nianhuaId;
//                } else {
//                    log.warn("无法识别景区，跳过表: {}", scenicName);
//                    continue;
//                }
//
//                for (int i = 1; i < rows.size(); i++) {
//                    XWPFTableRow row = rows.get(i);
//                    String spotName = getCellTextSafe(row, 2).trim();
//                    // 跳过表头行
//                    if (spotName.isEmpty() || spotName.equals("景点名称")) continue;
//
//                    // ScenicSpot 入库（去重）
//                    if (!insertedSpotNames.contains(spotName)) {
//                        String location   = getCellTextSafe(row, 3).trim();
//                        String params     = getCellTextSafe(row, 4).trim();
//                        String func       = getCellTextSafe(row, 5).trim();
//                        String detail     = getCellTextSafe(row, 7).trim();
//                        String highlight  = getCellTextSafe(row, 8).trim();
//                        String showInfo   = getCellTextSafe(row, 9).trim();
//                        String remark     = getCellTextSafe(row, 10).trim();
//
//                        ScenicSpot spot = new ScenicSpot();
//                        spot.setScenicId(scenicId);
//                        spot.setSpotName(spotName);
//                        spot.setSpotLocation(location);
//                        spot.setSpotDesc(truncate(func + " " + detail, 2000));
//                        spot.setVisitDuration(null);
//                        spot.setStatus(1);
//                        spot.setSort(0);
//                        spot.setCreateTime(new Date());
//                        spot.setUpdateTime(new Date());
//                        scenicSpotMapper.insert(spot);
//                        insertedSpotNames.add(spotName);
//                        spotNameCache.put(spotName, spot);
//                        spotCount++;
//                    }
//
//                    // KnowledgeDoc 入库（讲解词）
//                    StringBuilder content = new StringBuilder();
//                    if (!getCellTextSafe(row, 1).isEmpty()) content.append("【景点编号】").append(getCellTextSafe(row, 1)).append("\n");
//                    if (!getCellTextSafe(row, 3).isEmpty()) content.append("【位置】").append(getCellTextSafe(row, 3)).append("\n");
//                    if (!getCellTextSafe(row, 4).isEmpty()) content.append("【建筑/景观参数】").append(getCellTextSafe(row, 4)).append("\n");
//                    if (!getCellTextSafe(row, 5).isEmpty()) content.append("【核心功能】").append(getCellTextSafe(row, 5)).append("\n");
//                    if (!getCellTextSafe(row, 6).isEmpty()) content.append("【文化内涵】").append(getCellTextSafe(row, 6)).append("\n");
//                    if (!getCellTextSafe(row, 7).isEmpty()) content.append("【详细介绍】").append(getCellTextSafe(row, 7)).append("\n");
//                    if (!getCellTextSafe(row, 8).isEmpty()) content.append("【游玩亮点】").append(getCellTextSafe(row, 8)).append("\n");
//                    if (!getCellTextSafe(row, 9).isEmpty()) content.append("【演艺/开放信息】").append(getCellTextSafe(row, 9)).append("\n");
//                    if (!getCellTextSafe(row, 10).isEmpty()) content.append("【备注】").append(getCellTextSafe(row, 10)).append("\n");
//
//                    String docContent = content.toString();
//                    if (docContent.isEmpty()) continue;
//
//                    String funcStr = getCellTextSafe(row, 5).trim();
//                    String detail2 = getCellTextSafe(row, 7).trim();
//                    KnowledgeDoc doc = new KnowledgeDoc();
//                    doc.setScenicId(scenicId);
//                    doc.setDocTitle(spotName);
//                    doc.setDocType("讲解词");
//                    doc.setDocContent(docContent);
//                    doc.setDocSummary(funcStr.isEmpty() ? truncate(detail2, 200) : funcStr);
//                    doc.setWordCount(docContent.length());
//                    doc.setVectorized(0);
//                    doc.setCreator(1L);
//                    doc.setStatus(1);
//                    doc.setViewCount(0);
//                    doc.setCreateTime(new Date());
//                    doc.setUpdateTime(new Date());
//                    knowledgeDocMapper.insert(doc);
//                    docCount++;
//                }
//            }
//        }
//
//        log.info("景点DOCX - ScenicSpot: {} 条", spotCount);
//        log.info("景点DOCX - KnowledgeDoc: {} 条", docCount);
//        log.info("========== 景点结构化数据集 DOCX 导入完成 ==========");
//    }
//
//    // ==================== Step 3: 导览指南 DOCX ====================
//    private void importGuideDocx() throws Exception {
//        log.info("========== 开始导入导览指南 DOCX ==========");
//        int routeCount = 0;
//        int faqCount   = 0;
//        int spotCount  = 0;
//        int docCount   = 0;
//
//        try (FileInputStream fis = new FileInputStream(DOCX_GUIDE);
//             XWPFDocument document = new XWPFDocument(fis)) {
//
//            List<XWPFParagraph> paragraphs = document.getParagraphs();
//
//            // 状态机：当前章节
//            String currentTitle    = null;
//            String currentContent  = null;
//            boolean inRouteSection = false;
//            List<String> routeSpots = new ArrayList<>();
//
//            for (int i = 0; i < paragraphs.size(); i++) {
//                XWPFParagraph para = paragraphs.get(i);
//                String text = para.getText().trim();
//                if (text.isEmpty()) continue;
//
//                // 判断是否为路线章节（"游览路线"/"推荐路线"/"线路"/"主题路线"）
//                boolean isRouteHeading = isChapterHeading(text)
//                        && (text.contains("路线") || text.contains("线路") || text.contains("游览"));
//
//                // 判断是否为景点章节
//                boolean isSpotHeading = isChapterHeading(text)
//                        && (text.contains("景点") || text.contains("景区特色") || text.contains("必游"));
//
//                // 判断是否为FAQ章节
//                boolean isFaqHeading = isChapterHeading(text)
//                        && (text.contains("问答") || text.contains("FAQ") || text.contains("常见问题"));
//
//                // 保存前一个内容块
//                if (currentTitle != null && currentContent != null && currentContent.length() > 30) {
//                    if (inRouteSection) {
//                        // 生成路线
//                        TourRoute route = buildTourRoute(currentTitle, currentContent, routeSpots);
//                        if (route != null) {
//                            tourRouteMapper.insert(route);
//                            routeCount++;
//                        }
//                        routeSpots.clear();
//                        inRouteSection = false;
//                    } else if (currentTitle.contains("景点")) {
//                        // 景点章节 → ScenicSpot
//                        int added = importSpotsFromSection(currentTitle, currentContent);
//                        spotCount += added;
//                    } else {
//                        // 其他章节 → KnowledgeDoc
//                        KnowledgeDoc doc = buildKnowledgeDoc(currentTitle, currentContent, "文史资料");
//                        if (doc != null) {
//                            knowledgeDocMapper.insert(doc);
//                            docCount++;
//                        }
//                    }
//                }
//
//                // 提取FAQ（基于表格或Q&A格式）
//                List<FaqPair> faqs = extractFaqsFromParagraphs(paragraphs, i, text);
//                for (FaqPair faq : faqs) {
//                    Faq f = new Faq();
//                    f.setScenicId(lingshanId);
//                    f.setQuestion(faq.question);
//                    f.setAnswer(faq.answer);
//                    f.setAnswerType("text");
//                    f.setClickCount(0);
//                    f.setHelpfulCount(0);
//                    f.setUnhelpfulCount(0);
//                    f.setSimilarityThreshold(0.75);
//                    f.setCreator(1L);
//                    f.setStatus(1);
//                    f.setCreateTime(new Date());
//                    f.setUpdateTime(new Date());
//                    faqMapper.insert(f);
//                    faqCount++;
//                }
//
//                // 开始新章节
//                currentTitle   = text;
//                currentContent = "";
//                inRouteSection = isRouteHeading;
//
//                // 收集路线中的景点
//                if (isRouteHeading) {
//                    // 向前看几段收集景点
//                    for (int j = i + 1; j < Math.min(i + 30, paragraphs.size()); j++) {
//                        String sub = paragraphs.get(j).getText().trim();
//                        if (sub.isEmpty()) continue;
//                        if (isChapterHeading(sub)) break;
//                        routeSpots.add(sub);
//                    }
//                }
//            }
//
//            // 最后一个章节
//            if (currentTitle != null && currentContent != null && currentContent.length() > 30) {
//                if (inRouteSection) {
//                    TourRoute route = buildTourRoute(currentTitle, currentContent, routeSpots);
//                    if (route != null) {
//                        tourRouteMapper.insert(route);
//                        routeCount++;
//                    }
//                } else {
//                    KnowledgeDoc doc = buildKnowledgeDoc(currentTitle, currentContent, "文史资料");
//                    if (doc != null) {
//                        knowledgeDocMapper.insert(doc);
//                        docCount++;
//                    }
//                }
//            }
//
//            // 额外：从指南内容中生成推荐FAQ
//            int extraFaqs = generateFaqsFromGuide(document);
//            faqCount += extraFaqs;
//        }
//
//        log.info("导览指南 - TourRoute: {} 条", routeCount);
//        log.info("导览指南 - FAQ: {} 条", faqCount);
//        log.info("导览指南 - ScenicSpot: {} 条", spotCount);
//        log.info("导览指南 - KnowledgeDoc: {} 条", docCount);
//        log.info("========== 导览指南 DOCX 导入完成 ==========");
//    }
//
//    // 从段落提取引用表格中的FAQ
//    private List<FaqPair> extractFaqsFromParagraphs(List<XWPFParagraph> paragraphs, int idx, String text) {
//        List<FaqPair> faqs = new ArrayList<>();
//
//        // Q: xxx A: xxx 格式
//        Pattern qaPattern = Pattern.compile("[Qq]\\s*[:：]\\s*(.+?)\\s*[Aa]\\s*[:：]\\s*(.+)");
//        Matcher m = qaPattern.matcher(text);
//        while (m.find()) {
//            faqs.add(new FaqPair(m.group(1).trim(), m.group(2).trim()));
//        }
//
//        // 问：xxx 答：xxx 格式
//        Pattern chineseQa = Pattern.compile("[问题][：:]\\s*(.+?)\\s*[答回][：:]\\s*(.+)");
//        Matcher m2 = chineseQa.matcher(text);
//        while (m2.find()) {
//            faqs.add(new FaqPair(m2.group(1).trim(), m2.group(2).trim()));
//        }
//
//        return faqs;
//    }
//
//    // 从文档中批量生成FAQ
//    private int generateFaqsFromGuide(XWPFDocument document) {
//        // 基于灵山胜境的常见问题预生成
//        String[][] commonFaqs = {
//                {"灵山胜境在哪里？", "灵山胜境位于江苏省无锡市滨湖区马山太湖国家旅游度假区，是国家5A级旅游景区。"},
//                {"灵山胜境开放时间？", "灵山胜境一般开放时间为08:00-17:00，黄金周及节假日可能适当延长。具体以景区当日公告为准。"},
//                {"灵山胜境门票多少钱？", "灵山胜境门票价格约为210元/人，含灵山大佛、梵宫、五印坛城等景点。具体价格以景区官方公告为准。"},
//                {"拈花湾小镇怎么去？", "拈花湾位于无锡市滨湖区马山振兴街188号，距离灵山胜境约3公里，可乘坐景区接驳巴士或自驾前往。"},
//                {"灵山胜境有哪些景点？", "灵山胜境主要景点包括：灵山大佛、九龙灌浴、灵山梵宫、五印坛城、拈花湾禅意小镇等。"},
//                {"灵山胜境的最佳游览时间？", "灵山胜境四季皆宜，春季赏花、夏季避暑、秋季观景、冬季祈福各有特色。建议游览时长4-6小时。"},
//                {"梵宫有什么好玩的？", "灵山梵宫集成了中国传统工艺美术与佛教文化精髓，内有大型音乐动态壁画、佛教艺术珍品展、《灵山吉祥颂》演出等。"},
//                {"灵山大佛有多高？", "灵山大佛高88米，是世界上最高的释迦牟尼佛青铜立像，大佛脚下还有'天下第一掌'等配套景观。"},
//                {"拈花湾有什么特色？", "拈花湾以'禅意生活'为核心，打造了香月花街、拈花塔、拈花堂等禅意景观，每晚还有《禅行》灯光秀。"},
//                {"适合带小孩游览吗？", "灵山胜境适合全年龄段游客，景区内有儿童游乐区、无障碍设施齐全，亲子家庭游客也可轻松游览。"},
//        };
//
//        int count = 0;
//        for (String[] faq : commonFaqs) {
//            // 检查是否已存在相同问题
//            long existing = faqMapper.selectCount(
//                    new LambdaQueryWrapper<Faq>().eq(Faq::getQuestion, faq[0]));
//            if (existing > 0) continue;
//
//            Faq f = new Faq();
//            f.setScenicId(lingshanId);
//            f.setQuestion(faq[0]);
//            f.setAnswer(faq[1]);
//            f.setAnswerType("text");
//            f.setClickCount(100);
//            f.setHelpfulCount(50);
//            f.setUnhelpfulCount(0);
//            f.setSimilarityThreshold(0.75);
//            f.setCreator(1L);
//            f.setStatus(1);
//            f.setCreateTime(new Date());
//            f.setUpdateTime(new Date());
//            faqMapper.insert(f);
//            count++;
//        }
//        return count;
//    }
//
//    // 构建路线实体
//    private TourRoute buildTourRoute(String title, String content, List<String> spots) {
//        TourRoute route = new TourRoute();
//        route.setScenicId(lingshanId);
//        route.setRouteName(title);
//        route.setRouteDesc(truncate(content, 500));
//
//        // 推断路线类型
//        if (title.contains("历史") || title.contains("文化")) {
//            route.setRouteType("历史");
//            route.setRouteTheme("历史文化深度游");
//        } else if (title.contains("自然") || title.contains("山水")) {
//            route.setRouteType("自然");
//            route.setRouteTheme("自然风光之旅");
//        } else if (title.contains("亲子") || title.contains("家庭")) {
//            route.setRouteType("亲子");
//            route.setRouteTheme("亲子欢乐游");
//        } else if (title.contains("全景") || title.contains("精华")) {
//            route.setRouteType("全景");
//            route.setRouteTheme("全景精华游");
//        } else if (title.contains("小众") || title.contains("深度")) {
//            route.setRouteType("小众");
//            route.setRouteTheme("深度探索游");
//        } else {
//            route.setRouteType("全景");
//            route.setRouteTheme("综合导览游");
//        }
//
//        // 估算耗时（按内容字数）
//        int wordCount = content.length();
//        int estimatedMinutes = Math.min(480, Math.max(60, wordCount / 10));
//        route.setRouteTime(estimatedMinutes);
//        route.setRouteDistance(null); // 未知
//        route.setDifficultyLevel(estimateDifficulty(content));
//        route.setSuitableCrowd(estimateSuitableCrowd(title, content));
//        route.setBestSeason("四季皆宜");
//        route.setViewCount(0);
//        route.setBookCount(0);
//        route.setRating(4.5);
//        route.setSort(0);
//        route.setStatus(1);
//        route.setCreator(1L);
//        route.setCreateTime(new Date());
//        route.setUpdateTime(new Date());
//
//        // 路线标签
//        try {
//            List<String> tags = new ArrayList<>();
//            if (title.contains("全景")) tags.add("全景导览");
//            if (title.contains("亲子")) tags.add("亲子友好");
//            if (title.contains("无障碍")) tags.add("无障碍");
//            if (estimatedMinutes >= 240) tags.add("深度游");
//            else if (estimatedMinutes <= 120) tags.add("轻游览");
//            tags.add("首次推荐");
//            route.setRouteTags(tags);
//        } catch (Exception e) {
//            // ignore tag setting
//        }
//
//        return route;
//    }
//
//    private int estimateDifficulty(String content) {
//        if (content.contains("轻松") || content.contains("休闲")) return 1;
//        if (content.contains("徒步") || content.contains("登山") || content.contains("较长")) return 3;
//        return 2;
//    }
//
//    private String estimateSuitableCrowd(String title, String content) {
//        String all = title + " " + content;
//        if (all.contains("亲子") || all.contains("儿童")) return "亲子家庭";
//        if (all.contains("老人") || all.contains("老年")) return "老年游客";
//        if (all.contains("情侣")) return "情侣/夫妻";
//        if (all.contains("商务")) return "商务考察";
//        return "所有游客";
//    }
//
//    // 从章节内容中提取景点并入库
//    private int importSpotsFromSection(String sectionTitle, String content) {
//        int count = 0;
//        // 从内容中提取景点名（段落短句通常是景点名）
//        String[] lines = content.split("\n");
//        for (String line : lines) {
//            String trimmed = line.trim();
//            // 过滤掉太长的行（正文）或太短的行（空）
//            if (trimmed.length() < 2 || trimmed.length() > 50) continue;
//            // 跳过明显不是景点名的行
//            if (trimmed.matches(".*[。！？]$") && trimmed.length() > 20) continue; // 句子太长，不是景点名
//            if (trimmed.matches("^[一二三四五六七八九十\\d].*")) { // 序号开头的行
//                String spotName = trimmed.replaceFirst("^[一二三四五六七八九十\\d][、.、\\s]+", "").trim();
//                if (!spotName.isEmpty() && !insertedSpotNames.contains(spotName)) {
//                    ScenicSpot spot = new ScenicSpot();
//                    spot.setScenicId(lingshanId);
//                    spot.setSpotName(spotName);
//                    spot.setSpotType(inferSpotType(sectionTitle));
//                    spot.setSpotDesc(truncate(trimmed, 500));
//                    spot.setStatus(1);
//                    spot.setSort(0);
//                    spot.setCreateTime(new Date());
//                    spot.setUpdateTime(new Date());
//                    scenicSpotMapper.insert(spot);
//                    insertedSpotNames.add(spotName);
//                    count++;
//                }
//            }
//        }
//        return count;
//    }
//
//    private String inferSpotType(String sectionTitle) {
//        if (sectionTitle.contains("历史") || sectionTitle.contains("文化")) return "历史";
//        if (sectionTitle.contains("自然") || sectionTitle.contains("山水")) return "自然";
//        if (sectionTitle.contains("演艺") || sectionTitle.contains("娱乐")) return "娱乐";
//        return "人文";
//    }
//
//    // 构建知识文档
//    private KnowledgeDoc buildKnowledgeDoc(String title, String content, String docType) {
//        if (content == null || content.length() < 20) return null;
//        KnowledgeDoc doc = new KnowledgeDoc();
//        doc.setScenicId(lingshanId);
//        doc.setDocTitle(title);
//        doc.setDocType(docType);
//        doc.setDocContent(content);
//        doc.setDocSummary(truncate(content.replaceAll("\\s+", " "), 300));
//        doc.setWordCount(content.length());
//        doc.setVectorized(0);
//        doc.setCreator(1L);
//        doc.setStatus(1);
//        doc.setViewCount(0);
//        doc.setCreateTime(new Date());
//        doc.setUpdateTime(new Date());
//        return doc;
//    }
//
//    // ==================== Step 4: 生成游客感受度报告 ====================
//    private void generateVisitorAnalysis() {
//        log.info("========== 生成游客感受度报告 ==========");
//        AtomicInteger count = new AtomicInteger(0);
//
//        for (Long scenicId : List.of(lingshanId, nianhuaId, zhongguoId)) {
//            for (String statsType : List.of("daily", "weekly", "monthly")) {
//                VisitorAnalysis va = new VisitorAnalysis();
//                va.setScenicId(scenicId);
//                va.setStatsDate(new Date());
//                va.setStatsType(statsType);
//                va.setTotalInteractions(0);
//                va.setUniqueVisitors(0);
//                va.setAvgSessionDuration(120);
//                va.setFocusPoints("[\"景点信息\",\"历史文化\",\"游览路线\",\"门票价格\",\"开放时间\"]");
//                va.setEmotionDistribution("{\"positive\":65,\"neutral\":25,\"negative\":10}");
//                va.setSatisfactionRate(BigDecimal.valueOf(4.2));
//                va.setSuggestionSummary("建议增加亲子互动设施，优化旺季排队管理，加强历史文化讲解深度。");
//                va.setAiGenerated(1);
//                va.setCreator(1L);
//                va.setCreateTime(new Date());
//                va.setUpdateTime(new Date());
//                visitorAnalysisMapper.insert(va);
//                count.incrementAndGet();
//            }
//        }
//
//        log.info("游客感受度报告生成: {} 条", count.get());
//        log.info("========== 游客感受度报告生成完成 ==========");
//    }
//
//    // ==================== 工具方法 ====================
//
//    private boolean isHeading(XWPFParagraph para, String text) {
//        if (text.length() > 40) return false;
//        try {
//            if (para.getCTP().getPPr() != null && para.getCTP().getPPr().getJc() != null) {
//                return true;
//            }
//        } catch (Exception ignored) {}
//        String style = para.getStyle();
//        if (style != null && (style.contains("Heading") || style.contains("Title"))) {
//            return true;
//        }
//        return false;
//    }
//
//    private boolean isChapterHeading(String text) {
//        String[] keywords = {
//                "概况", "历史", "缘起", "兴衰", "崛起", "文化", "内涵",
//                "景点", "特色", "路线", "贴士", "门票", "游览时间",
//                "餐饮", "住宿", "景区等级", "景区介绍", "导览",
//                "游览", "线路", "推荐", "主题"
//        };
//        for (String kw : keywords) {
//            if (text.contains(kw)) return true;
//        }
//        return false;
//    }
//
//    private String getCellTextSafe(XWPFTableRow row, int cellIndex) {
//        if (cellIndex >= row.getTableCells().size()) return "";
//        return Optional.ofNullable(row.getCell(cellIndex).getText())
//                .orElse("")
//                .replaceAll("\\s+", " ")
//                .trim();
//    }
//
//    private String getCellString(Cell cell) {
//        if (cell == null) return null;
//        try {
//            return switch (cell.getCellType()) {
//                case STRING    -> cell.getStringCellValue().trim();
//                case NUMERIC   -> String.valueOf((long) cell.getNumericCellValue());
//                case BOOLEAN   -> String.valueOf(cell.getBooleanCellValue());
//                case FORMULA   -> cell.getCellFormula();
//                default        -> "";
//            };
//        } catch (Exception e) {
//            return "";
//        }
//    }
//
//    private String getCellStringSafe(Row row, int colIdx) {
//        if (row == null || colIdx < 0) return "";
//        Cell cell = row.getCell(colIdx);
//        if (cell == null) return "";
//        return getCellString(cell);
//    }
//
//    private Date getCellDateSafe(Row row, int colIdx) {
//        if (row == null || colIdx < 0) return null;
//        Cell cell = row.getCell(colIdx);
//        if (cell == null) return null;
//        try {
//            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
//                return cell.getDateCellValue();
//            }
//        } catch (Exception ignored) {}
//        return null;
//    }
//
//    private Integer getCellIntSafe(Row row, int colIdx) {
//        if (row == null || colIdx < 0) return null;
//        Cell cell = row.getCell(colIdx);
//        if (cell == null) return null;
//        try {
//            if (cell.getCellType() == CellType.NUMERIC) {
//                return (int) cell.getNumericCellValue();
//            } else if (cell.getCellType() == CellType.STRING) {
//                String v = cell.getStringCellValue().trim();
//                return v.isEmpty() ? null : Integer.parseInt(v);
//            }
//        } catch (Exception ignored) {}
//        return null;
//    }
//
//    private Double getCellDoubleSafe(Row row, int colIdx) {
//        if (row == null || colIdx < 0) return null;
//        Cell cell = row.getCell(colIdx);
//        if (cell == null) return null;
//        try {
//            if (cell.getCellType() == CellType.NUMERIC) {
//                return cell.getNumericCellValue();
//            } else if (cell.getCellType() == CellType.STRING) {
//                String v = cell.getStringCellValue().trim();
//                return v.isEmpty() ? null : Double.parseDouble(v);
//            }
//        } catch (Exception ignored) {}
//        return null;
//    }
//
//    private String truncate(String s, int maxLen) {
//        if (s == null) return "";
//        if (s.length() <= maxLen) return s;
//        return s.substring(0, maxLen);
//    }
//
//    // ==================== 内部类 ====================
//    private static class ExcelRow {
//        Long scenicId;
//        String date;
//        Integer satisfaction;
//        float totalCost;
//        float ticketCost;
//        Float stayDuration;
//        String touristId;
//        int groupSize;
//
//        ExcelRow(Long scenicId, String date, Integer satisfaction,
//                 float totalCost, float ticketCost, Float stayDuration,
//                 String touristId, int groupSize) {
//            this.scenicId = scenicId;
//            this.date = date;
//            this.satisfaction = satisfaction;
//            this.totalCost = totalCost;
//            this.ticketCost = ticketCost;
//            this.stayDuration = stayDuration;
//            this.touristId = touristId;
//            this.groupSize = groupSize;
//        }
//    }
//
//    /**
//     * 通用批量插入 VisitorInteraction（使用 MyBatis SqlSession batch 模式）
//     */
//    private void batchInsertVisitorInteraction(List<VisitorInteraction> list) {
//        if (list == null || list.isEmpty()) return;
//        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
//            VisitorInteractionMapper mapper = session.getMapper(VisitorInteractionMapper.class);
//            for (VisitorInteraction entity : list) {
//                mapper.insert(entity);
//            }
//            session.flushStatements();
//        } catch (Exception e) {
//            log.warn("VisitorInteraction 批量插入失败: {}", e.getMessage(), e);
//        }
//    }
//
//    /**
//     * 通用批量插入 OperationStats（使用 MyBatis SqlSession batch 模式）
//     */
//    private void batchInsertOperationStats(List<OperationStats> list) {
//        if (list == null || list.isEmpty()) return;
//        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
//            OperationStatsMapper mapper = session.getMapper(OperationStatsMapper.class);
//            for (OperationStats entity : list) {
//                mapper.insert(entity);
//            }
//            session.flushStatements();
//        } catch (Exception e) {
//            log.warn("OperationStats 批量插入失败: {}", e.getMessage(), e);
//        }
//    }
//
//    private static class FaqPair {
//        String question;
//        String answer;
//
//        FaqPair(String question, String answer) {
//            this.question = question;
//            this.answer = answer;
//        }
//    }
//}
