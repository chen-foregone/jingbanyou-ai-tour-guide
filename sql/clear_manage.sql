-- ====================== 一键删除 manage 库（慎用！不可逆） ======================
-- 删除所有 manage_ 前缀表，按依赖顺序（子表先删）
-- 执行前请务必备份！

USE jingbanyou;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `manage_visitor_conversation`;
DROP TABLE IF EXISTS `manage_visitor_interaction`;
DROP TABLE IF EXISTS `manage_visitor_analysis`;
DROP TABLE IF EXISTS `manage_operation_stats`;
DROP TABLE IF EXISTS `manage_route_spot_relation`;
DROP TABLE IF EXISTS `manage_tour_route`;
DROP TABLE IF EXISTS `manage_knowledge_chunk`;
DROP TABLE IF EXISTS `manage_knowledge_doc`;
DROP TABLE IF EXISTS `manage_faq`;
DROP TABLE IF EXISTS `manage_scenic_spot`;
DROP TABLE IF EXISTS `manage_digital_human_action`;
DROP TABLE IF EXISTS `manage_digital_human_config`;
DROP TABLE IF EXISTS `manage_scenic_area`;
DROP TABLE IF EXISTS `manage_config`;
DROP TABLE IF EXISTS `manage_admin`;

SET FOREIGN_KEY_CHECKS = 1;

SELECT 'manage_ 前缀表已全部删除' AS result;
