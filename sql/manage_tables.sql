-- ====================== 景区导览 AI 数字人 - 管理后台表结构 ======================
-- 合并到 jingbanyou 数据库，使用 manage_ 前缀避免与若依框架冲突
-- 字符集：utf8mb4，排序规则：utf8mb4_general_ci

USE jingbanyou;

-- ====================== 1. 系统管理员表（后台登录/权限） ======================
DROP TABLE IF EXISTS `manage_admin`;
CREATE TABLE `manage_admin` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '管理员 ID',
    `username` VARCHAR(32) NOT NULL UNIQUE COMMENT '登录账号',
    `password` VARCHAR(64) NOT NULL COMMENT '登录密码 (加密存储)',
    `real_name` VARCHAR(20) NOT NULL COMMENT '真实姓名',
    `role` TINYINT NOT NULL DEFAULT 1 COMMENT '角色 1-普通管理员 2-超级管理员',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-正常',
    `last_login_time` DATETIME COMMENT '最后登录时间',
    `last_login_ip` VARCHAR(50) COMMENT '最后登录 IP',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_username` (`username`),
    INDEX `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统管理员表';

-- ====================== 2. 景区基础信息表（赛题示范景区） ======================
DROP TABLE IF EXISTS `manage_scenic_area`;
CREATE TABLE `manage_scenic_area` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '景区 ID',
    `scenic_name` VARCHAR(100) NOT NULL COMMENT '景区名称',
    `scenic_address` VARCHAR(255) NOT NULL COMMENT '景区地址',
    `scenic_desc` TEXT COMMENT '景区总体介绍',
    `open_time` VARCHAR(100) COMMENT '开放时间',
    `ticket_info` TEXT COMMENT '门票信息',
    `contact_phone` VARCHAR(20) COMMENT '联系电话',
    `official_website` VARCHAR(255) COMMENT '官方网站',
    `cover_image` VARCHAR(255) COMMENT '景区封面图',
    `star_level` VARCHAR(10) COMMENT '景区等级 (如 5A、4A)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    `sort` INT DEFAULT 0 COMMENT '排序权重',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_scenic_name` (`scenic_name`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='景区基础信息表';

-- ====================== 3. 景区景点信息表（核心讲解对象） ======================
DROP TABLE IF EXISTS `manage_scenic_spot`;
CREATE TABLE `manage_scenic_spot` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '景点 ID',
    `scenic_id` BIGINT NOT NULL COMMENT '所属景区 ID',
    `spot_name` VARCHAR(100) NOT NULL COMMENT '景点名称',
    `spot_type` VARCHAR(20) COMMENT '景点类型 历史/自然/人文/娱乐',
    `spot_location` VARCHAR(255) COMMENT '景点位置',
    `spot_desc` TEXT NOT NULL COMMENT '景点详细介绍 (文史/特色)',
    `audio_guide` VARCHAR(255) COMMENT '语音讲解文件地址',
    `video_guide` VARCHAR(255) COMMENT '视频讲解文件地址',
    `cover_image` VARCHAR(255) COMMENT '景点封面图',
    `gallery_images` JSON COMMENT '图片集 (JSON 数组)',
    `visit_duration` INT COMMENT '建议游览时长 (分钟)',
    `suitable_season` VARCHAR(100) COMMENT '适宜游览季节',
    `sort` INT DEFAULT 0 COMMENT '排序权重',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`scenic_id`) REFERENCES `manage_scenic_area`(`id`) ON DELETE CASCADE,
    INDEX `idx_scenic_id` (`scenic_id`),
    INDEX `idx_spot_name` (`spot_name`),
    INDEX `idx_spot_type` (`spot_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='景区景点信息表';

-- ====================== 4. 景区知识库文档表（本地知识库核心/RAG 数据源） ======================
DROP TABLE IF EXISTS `manage_knowledge_doc`;
CREATE TABLE `manage_knowledge_doc` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文档 ID',
    `scenic_id` BIGINT NOT NULL COMMENT '所属景区 ID',
    `doc_title` VARCHAR(200) NOT NULL COMMENT '文档标题',
    `doc_type` VARCHAR(20) NOT NULL COMMENT '文档类型 讲解词/文史资料/攻略/公告/FAQ',
    `doc_content` LONGTEXT NOT NULL COMMENT '文档完整内容',
    `doc_summary` TEXT COMMENT '文档摘要 (自动提取)',
    `doc_file` VARCHAR(500) COMMENT '附件地址 (可选，支持 pdf/doc/txt)',
    `file_size` BIGINT COMMENT '文件大小 (字节)',
    `word_count` INT COMMENT '字数统计',
    `chunk_count` INT DEFAULT 0 COMMENT '切片数量',
    `vectorized` TINYINT DEFAULT 0 COMMENT '是否已向量化 0-否 1-是',
    `embedding_model` VARCHAR(50) COMMENT 'Embedding 模型版本',
    `creator` BIGINT NOT NULL COMMENT '创建人 (管理员 ID)',
    `updater` BIGINT COMMENT '最后更新人',
    `view_count` INT DEFAULT 0 COMMENT '被引用次数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`scenic_id`) REFERENCES `manage_scenic_area`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`creator`) REFERENCES `manage_admin`(`id`),
    FOREIGN KEY (`updater`) REFERENCES `manage_admin`(`id`),
    INDEX `idx_scenic_id` (`scenic_id`),
    INDEX `idx_doc_type` (`doc_type`),
    INDEX `idx_vectorized` (`vectorized`),
    FULLTEXT INDEX `ft_doc_content` (`doc_content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='景区知识库文档表';

-- ====================== 5. 知识文档切片表（RAG 检索核心单元） ======================
DROP TABLE IF EXISTS `manage_knowledge_chunk`;
CREATE TABLE `manage_knowledge_chunk` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '切片 ID',
    `doc_id` BIGINT NOT NULL COMMENT '所属文档 ID',
    `chunk_index` INT NOT NULL COMMENT '切片序号 (从 0 开始)',
    `chunk_content` TEXT NOT NULL COMMENT '切片内容',
    `chunk_tokens` INT COMMENT 'Token 数量',
    `vector_id` VARCHAR(128) COMMENT '向量 ID (Redis Stack)',
    `embedding_version` VARCHAR(50) COMMENT 'Embedding 模型版本',
    `metadata` JSON COMMENT '元数据 (页码/章节/标签等)',
    `similarity_avg` DECIMAL(5,4) COMMENT '平均相似度 (用于优化)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`doc_id`) REFERENCES `manage_knowledge_doc`(`id`) ON DELETE CASCADE,
    INDEX `idx_doc_id` (`doc_id`),
    INDEX `idx_vector_id` (`vector_id`),
    INDEX `idx_chunk_index` (`chunk_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档切片表 (RAG 核心)';

-- ====================== 6. 常见问答表（FAQ，保障问答准确率 90%） ======================
DROP TABLE IF EXISTS `manage_faq`;
CREATE TABLE `manage_faq` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '问答 ID',
    `scenic_id` BIGINT NOT NULL COMMENT '所属景区 ID',
    `question` TEXT NOT NULL COMMENT '用户问题 (标准问法)',
    `question_keywords` VARCHAR(500) COMMENT '问题关键词 (逗号分隔，Redis Stack 全文检索已覆盖此功能，仅保留供后台管理筛选用)',
    `answer` TEXT NOT NULL COMMENT '标准回答',
    `answer_type` VARCHAR(20) DEFAULT 'text' COMMENT '回答类型 text/rich/html',
    `spot_id` BIGINT DEFAULT NULL COMMENT '关联景点 ID(可选)',
    `similar_questions` JSON COMMENT '相似问法 (JSON 数组扩展)',
    `click_count` INT DEFAULT 0 COMMENT '被咨询次数 (数据大屏用)',
    `helpful_count` INT DEFAULT 0 COMMENT '点赞数',
    `unhelpful_count` INT DEFAULT 0 COMMENT '踩数',
    `vector_id` VARCHAR(128) COMMENT '问题向量 ID',
    `similarity_threshold` DECIMAL(5,4) DEFAULT 0.85 COMMENT '相似度阈值',
    `creator` BIGINT NOT NULL COMMENT '创建人',
    `updater` BIGINT COMMENT '更新人',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`scenic_id`) REFERENCES `manage_scenic_area`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`spot_id`) REFERENCES `manage_scenic_spot`(`id`) ON DELETE SET NULL,
    INDEX `idx_scenic_id` (`scenic_id`),
    INDEX `idx_spot_id` (`spot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='景区常见问答表 (FAQ)';

-- ====================== 7. AI 数字人形象配置表（2D 方案） ======================
DROP TABLE IF EXISTS `manage_digital_human_config`;
CREATE TABLE `manage_digital_human_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置 ID',
    `scenic_id` BIGINT NOT NULL COMMENT '所属景区 ID',
    `human_name` VARCHAR(50) NOT NULL COMMENT '数字人名称',
    `appearance_config` JSON COMMENT '外观配置 (2D 驱动参数 JSON，如模型选择、增强开关等)',
    `voice_config` JSON NOT NULL COMMENT '语音合成参数 (TTS 模型/音色/语速/音调)',
    `lip_sync` TINYINT DEFAULT 1 COMMENT '口型同步 0-关闭 1-开启',
    `default_greeting` TEXT COMMENT '默认问候语',
    `avatar_image` VARCHAR(255) COMMENT '人物图片地址 (2D 驱动源图)',
    `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认 0-否 1-是',
    `creator` BIGINT NOT NULL COMMENT '配置人',
    `updater` BIGINT COMMENT '更新人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`scenic_id`) REFERENCES `manage_scenic_area`(`id`) ON DELETE CASCADE,
    INDEX `idx_scenic_id` (`scenic_id`),
    INDEX `idx_is_default` (`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 数字人形象配置表 (2D 方案)';

-- ====================== 8. 数字人动作库表（表情/手势/肢体） ======================
DROP TABLE IF EXISTS `manage_digital_human_action`;
CREATE TABLE `manage_digital_human_action` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '动作 ID',
    `action_name` VARCHAR(50) NOT NULL COMMENT '动作名称',
    `action_category` VARCHAR(20) NOT NULL COMMENT '分类 expression/gesture/body',
    `emotion_tag` VARCHAR(20) COMMENT '情感标签 happy/sad/neutral/excited',
    `action_data` JSON NOT NULL COMMENT '动作数据 (BlendShapes/骨骼动画)',
    `trigger_keyword` VARCHAR(200) COMMENT '触发关键词',
    `duration` INT COMMENT '持续时间 (毫秒)',
    `priority` INT DEFAULT 0 COMMENT '优先级 (数值越大优先级越高)',
    `is_builtin` TINYINT DEFAULT 1 COMMENT '是否内置 0-自定义 1-内置',
    `usage_count` INT DEFAULT 0 COMMENT '使用次数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_category` (`action_category`),
    INDEX `idx_emotion` (`emotion_tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字人动作库表';

-- ====================== 9. 个性化游览路线表 ======================
DROP TABLE IF EXISTS `manage_tour_route`;
CREATE TABLE `manage_tour_route` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '路线 ID',
    `scenic_id` BIGINT NOT NULL COMMENT '所属景区 ID',
    `route_name` VARCHAR(100) NOT NULL COMMENT '路线名称',
    `route_type` VARCHAR(20) NOT NULL COMMENT '路线类型 历史/自然/亲子/全景/小众/无障碍',
    `route_theme` VARCHAR(100) COMMENT '路线主题',
    `route_time` INT COMMENT '预计耗时 (分钟)',
    `route_distance` DECIMAL(8,2) COMMENT '路线总长度 (公里)',
    `route_desc` TEXT COMMENT '路线介绍',
    `suitable_crowd` VARCHAR(100) COMMENT '适宜人群',
    `best_season` VARCHAR(50) COMMENT '最佳游览季节',
    `difficulty_level` TINYINT DEFAULT 1 COMMENT '难度等级 1-简单 2-中等 3-困难',
    `cover_image` VARCHAR(255) COMMENT '路线封面图',
    `route_map` VARCHAR(255) COMMENT '路线地图',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `view_count` INT DEFAULT 0 COMMENT '浏览次数',
    `book_count` INT DEFAULT 0 COMMENT '预订次数',
    `rating` DECIMAL(3,2) DEFAULT 0.00 COMMENT '评分 (0-5)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    `creator` BIGINT COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`scenic_id`) REFERENCES `manage_scenic_area`(`id`) ON DELETE CASCADE,
    INDEX `idx_scenic_id` (`scenic_id`),
    INDEX `idx_route_type` (`route_type`),
    INDEX `idx_difficulty` (`difficulty_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个性化游览路线表';

-- ====================== 10. 路线 - 景点关联表（多对多） ======================
DROP TABLE IF EXISTS `manage_route_spot_relation`;
CREATE TABLE `manage_route_spot_relation` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '关联 ID',
    `route_id` BIGINT NOT NULL COMMENT '路线 ID',
    `spot_id` BIGINT NOT NULL COMMENT '景点 ID',
    `visit_order` INT NOT NULL COMMENT '游览顺序 (从 1 开始)',
    `stay_duration` INT COMMENT '建议停留时间 (分钟)',
    `transport_method` VARCHAR(20) COMMENT '交通方式 walking/shuttle/bicycle',
    `distance_to_next` DECIMAL(8,2) COMMENT '到下一景点距离 (公里)',
    `explanation_text` TEXT COMMENT '该景点讲解词',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`route_id`) REFERENCES `manage_tour_route`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`spot_id`) REFERENCES `manage_scenic_spot`(`id`) ON DELETE CASCADE,
    UNIQUE KEY `uk_route_spot` (`route_id`, `spot_id`),
    INDEX `idx_route_id` (`route_id`),
    INDEX `idx_visit_order` (`visit_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路线 - 景点关联表';

-- ====================== 11. 游客交互记录表（多模态交互日志） ======================
DROP TABLE IF EXISTS `manage_visitor_interaction`;
CREATE TABLE `manage_visitor_interaction` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '交互 ID',
    `scenic_id` BIGINT NOT NULL COMMENT '景区 ID',
    `human_id` BIGINT NOT NULL COMMENT '使用的数字人 ID',
    `session_id` VARCHAR(64) NOT NULL COMMENT '会话 ID(用于追踪多轮对话)',
    `visitor_id` VARCHAR(64) COMMENT '游客标识 (OpenID/匿名 UUID)',
    `interaction_type` VARCHAR(10) NOT NULL COMMENT '交互类型 text/voice/video',
    `user_question` TEXT NOT NULL COMMENT '游客问题',
    `user_voice_url` VARCHAR(500) COMMENT '用户语音 URL(如果是语音交互)',
    `ai_answer` TEXT NOT NULL COMMENT '数字人回答',
    `ai_voice_url` VARCHAR(500) COMMENT 'AI 语音 URL',
    `ai_video_url` VARCHAR(500) COMMENT 'AI 数字人视频 URL',
    `emotion_detected` VARCHAR(20) COMMENT '识别到的用户情感 positive/neutral/negative',
    `emotion_confidence` DECIMAL(5,4) COMMENT '情感置信度',
    `intent_type` VARCHAR(50) COMMENT '意图类型 inquiry/complaint/suggestion/praise',
    `rag_used` TINYINT DEFAULT 0 COMMENT '是否使用 RAG 检索 0-否 1-是',
    `rag_docs` JSON COMMENT '引用的知识文档 (JSON 数组)',
    `response_time_ms` INT COMMENT '响应耗时 (毫秒，5000 达标)',
    `tokens_used` INT COMMENT '消耗 Token 数量',
    `model_used` VARCHAR(50) COMMENT '使用的 AI 模型',
    `feedback_score` TINYINT COMMENT '用户评分 1-5',
    `feedback_text` VARCHAR(500) COMMENT '用户反馈',
    `device_type` VARCHAR(50) COMMENT '设备类型 mobile/desktop/kiosk',
    `ip_address` VARCHAR(50) COMMENT 'IP 地址',
    `location_info` VARCHAR(255) COMMENT '地理位置信息',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`scenic_id`) REFERENCES `manage_scenic_area`(`id`),
    FOREIGN KEY (`human_id`) REFERENCES `manage_digital_human_config`(`id`),
    INDEX `idx_scenic_id` (`scenic_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_visitor_id` (`visitor_id`),
    INDEX `idx_create_time` (`create_time`),
    INDEX `idx_emotion` (`emotion_detected`),
    INDEX `idx_intent` (`intent_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游客 - 数字人交互记录表';

-- ====================== 12. 游客感受度分析报告表 ======================
DROP TABLE IF EXISTS `manage_visitor_analysis`;
CREATE TABLE `manage_visitor_analysis` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分析 ID',
    `scenic_id` BIGINT NOT NULL COMMENT '景区 ID',
    `stats_date` DATE NOT NULL COMMENT '统计日期',
    `stats_type` VARCHAR(10) NOT NULL COMMENT '统计类型 daily/weekly/monthly',
    `total_interactions` INT DEFAULT 0 COMMENT '总交互次数',
    `unique_visitors` INT DEFAULT 0 COMMENT '独立访客数',
    `avg_session_duration` INT COMMENT '平均会话时长 (秒)',
    `focus_points` JSON COMMENT '游客关注点 TOP10(JSON 数组)',
    `emotion_distribution` JSON COMMENT '情感分布 {positive:0.6, neutral:0.3, negative:0.1}',
    `satisfaction_rate` DECIMAL(5,2) COMMENT '满意度 (0-100)',
    `suggestion_summary` TEXT COMMENT '建议汇总',
    `ai_generated` TINYINT DEFAULT 0 COMMENT '是否 AI 生成报告 0-人工 1-AI',
    `creator` BIGINT COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`scenic_id`) REFERENCES `manage_scenic_area`(`id`),
    UNIQUE KEY `uk_date_type` (`scenic_id`, `stats_date`, `stats_type`),
    INDEX `idx_stats_date` (`stats_date`),
    INDEX `idx_stats_type` (`stats_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游客感受度分析报告表';

-- ====================== 13. 景区运营统计表（数据大屏） ======================
DROP TABLE IF EXISTS `manage_operation_stats`;
CREATE TABLE `manage_operation_stats` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `scenic_id` BIGINT NOT NULL COMMENT '景区 ID',
    `stat_date` DATE NOT NULL COMMENT '统计日期',
    `total_interactions` INT DEFAULT 0 COMMENT '总交互次数',
    `unique_visitors` INT DEFAULT 0 COMMENT '独立访客数',
    `avg_response_time_ms` INT DEFAULT 0 COMMENT '平均响应时间 (ms)',
    `avg_satisfaction` DECIMAL(3,2) DEFAULT 0.00 COMMENT '平均满意度',
    `text_count` INT DEFAULT 0 COMMENT '文本交互次数',
    `voice_count` INT DEFAULT 0 COMMENT '语音交互次数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (`scenic_id`) REFERENCES `manage_scenic_area`(`id`),
    UNIQUE KEY `uk_scenic_date` (`scenic_id`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营统计日汇总表';

-- ====================== 14. 系统全局配置表 ======================
DROP TABLE IF EXISTS `manage_config`;
CREATE TABLE `manage_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置 ID',
    `config_key` VARCHAR(50) NOT NULL UNIQUE COMMENT '配置键',
    `config_value` TEXT NOT NULL COMMENT '配置值',
    `config_type` VARCHAR(20) DEFAULT 'text' COMMENT '配置类型 text/json/number/boolean',
    `config_group` VARCHAR(50) COMMENT '配置分组 ai/ui/business/security',
    `config_desc` VARCHAR(255) COMMENT '配置说明',
    `editable` TINYINT DEFAULT 1 COMMENT '是否可编辑 0-否 1-是',
    `updater` BIGINT COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_config_key` (`config_key`),
    INDEX `idx_config_group` (`config_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统全局配置表';

-- ====================== 初始化数据 ======================

-- 插入默认管理员（密码为 admin123，实际应加密）
INSERT INTO `manage_admin` (`username`, `password`, `real_name`, `role`) VALUES 
('admin', '$2a$10$7JBthYzS4JZ.z6XqMlFwvOYhNkPQqKqVqVqVqVqVqVqVqVqVqVqVq', '系统管理员', 2);

-- 插入系统配置初始值
INSERT INTO `manage_config` (`config_key`, `config_value`, `config_type`, `config_group`, `config_desc`) VALUES
('ai.model.name', 'qwen-turbo', 'text', 'ai', 'AI 模型名称'),
('ai.temperature', '0.7', 'number', 'ai', '生成温度参数'),
('ai.max_tokens', '2000', 'number', 'ai', '最大 Token 数'),
('ai.rag.enabled', 'true', 'boolean', 'ai', '是否启用 RAG'),
('ai.rag.top_k', '3', 'number', 'ai', 'RAG 检索返回数量'),
('ai.rag.similarity_threshold', '0.75', 'number', 'ai', 'RAG 相似度阈值'),
('ui.theme', 'default', 'text', 'ui', '界面主题'),
('ui.language', 'zh-CN', 'text', 'ui', '界面语言'),
('business.response_timeout', '5000', 'number', 'business', '响应超时时间 (ms)'),
('security.session_timeout', '1800', 'number', 'security', '会话超时时间 (秒)');
