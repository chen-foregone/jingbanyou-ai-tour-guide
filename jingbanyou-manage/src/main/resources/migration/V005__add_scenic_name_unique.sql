-- ==============================================================
-- V005__add_scenic_name_unique.sql
-- 用途：为 manage_scenic_area 表的 scenic_name 字段添加唯一约束
-- 作者：jingbanyou
-- 日期：2026-05-12
-- ==============================================================
--
-- 注意：执行本脚本前，必须先清理 manage_scenic_area 表中存在的重复数据。
-- 重复数据清理示例（保留 id 较小的一条）：
--
--   DELETE t1 FROM manage_scenic_area t1
--   INNER JOIN manage_scenic_area t2
--   WHERE t1.id > t2.id AND t1.scenic_name = t2.scenic_name;
--
-- ==============================================================

ALTER TABLE manage_scenic_area ADD CONSTRAINT uk_scenic_name UNIQUE (scenic_name);
