-- =====================================================
-- 景伴游管理后台 - 若依用户体系初始化 SQL
-- 在若依系统初始化SQL之后执行
-- =====================================================

-- 1. 新增景区管理员角色
INSERT INTO sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
VALUES ('景区管理员', 'scenic_admin', 2, '1', 1, 1, '0', '0', 'admin', NOW(), '景区运营管理员，可访问管理后台所有功能');

-- 2. 新增默认景区管理员账号（密码：admin123）
INSERT INTO sys_user (dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, create_by, create_time, remark)
VALUES (103, 'scenic_admin', '景区管理员', '00', '', '', '0', '', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE36mggaZqZ4tMWIy', '0', '0', '127.0.0.1', NOW(), 'admin', NOW(), '景区运营默认管理员');

-- 3. 为景区管理员分配角色（user_id取上一步插入的ID）
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u, sys_role r
WHERE u.user_name = 'scenic_admin' AND r.role_key = 'scenic_admin';

-- 4. 新增管理后台菜单（目录）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('管理后台', 0, 10, 'manage', NULL, 1, 0, 'M', '0', '0', '', 'guide', 'admin', NOW(), '景伴游管理后台');

-- 5. 新增子菜单（parent_id 取上一步插入的 menu_id）
-- 景区管理
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
SELECT '景区管理', menu_id, 1, 'scenic', 'manage/scenic/index', 1, 0, 'C', '0', '0', 'manage:scenic:list', 'tree', 'admin', NOW()
FROM sys_menu WHERE menu_name = '管理后台' AND parent_id = 0;

-- 知识库管理
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
SELECT '知识库', menu_id, 2, 'knowledge', 'manage/knowledge/index', 1, 0, 'C', '0', '0', 'manage:knowledge:list', 'documentation', 'admin', NOW()
FROM sys_menu WHERE menu_name = '管理后台' AND parent_id = 0;

-- FAQ管理
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
SELECT 'FAQ问答', menu_id, 3, 'faq', 'manage/faq/index', 1, 0, 'C', '0', '0', 'manage:faq:list', 'question', 'admin', NOW()
FROM sys_menu WHERE menu_name = '管理后台' AND parent_id = 0;

-- 数字人配置
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
SELECT '数字人配置', menu_id, 4, 'digital-human', 'manage/digitalHuman/index', 1, 0, 'C', '0', '0', 'manage:digital-human:list', 'robot', 'admin', NOW()
FROM sys_menu WHERE menu_name = '管理后台' AND parent_id = 0;

-- 运营统计
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
SELECT '运营统计', menu_id, 5, 'stats', 'manage/stats/index', 1, 0, 'C', '0', '0', 'manage:stats:list', 'chart', 'admin', NOW()
FROM sys_menu WHERE menu_name = '管理后台' AND parent_id = 0;

-- 访客分析
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time)
SELECT '访客分析', menu_id, 6, 'analysis', 'manage/analysis/index', 1, 0, 'C', '0', '0', 'manage:analysis:list', 'peoples', 'admin', NOW()
FROM sys_menu WHERE menu_name = '管理后台' AND parent_id = 0;

-- 6. 为 scenic_admin 角色分配管理后台所有菜单权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r, sys_menu m
WHERE r.role_key = 'scenic_admin'
  AND m.menu_name IN ('管理后台', '景区管理', '知识库', 'FAQ问答', '数字人配置', '运营统计', '访客分析');
