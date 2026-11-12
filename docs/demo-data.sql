-- =============================================================
-- 演示数据（可重复执行，不会产生重复账号）
-- 密码统一 123456（复用已有 BCrypt 哈希）
-- 用法：
--   mysql -uroot -p123456 -P3307 --default-character-set=utf8mb4 < demo-data.sql
-- =============================================================
USE portfolio;
SET NAMES utf8mb4;

-- ---------- 1. 组织：再补 1 个学院 / 1 个系 / 2 个班 ----------
INSERT INTO org_unit (id, name, type, parent_id, path, sort)
SELECT 5, '计算机与人工智能学院', 2, 1, '/1/5/', 1 WHERE NOT EXISTS (SELECT 1 FROM org_unit WHERE id = 5);
INSERT INTO org_unit (id, name, type, parent_id, path, sort)
SELECT 6, '计算机科学与技术系', 3, 5, '/1/5/6/', 0 WHERE NOT EXISTS (SELECT 1 FROM org_unit WHERE id = 6);
INSERT INTO org_unit (id, name, type, parent_id, path, sort)
SELECT 7, '计算机2025级1班', 4, 6, '/1/5/6/7/', 0 WHERE NOT EXISTS (SELECT 1 FROM org_unit WHERE id = 7);
INSERT INTO org_unit (id, name, type, parent_id, path, sort)
SELECT 8, '软件工程2025级2班', 4, 3, '/1/2/3/8/', 1 WHERE NOT EXISTS (SELECT 1 FROM org_unit WHERE id = 8);

INSERT INTO org_class (org_id, department_id, enrollment_date, graduation_date)
SELECT 7, 6, '2025-09-01', '2029-06-30' WHERE NOT EXISTS (SELECT 1 FROM org_class WHERE org_id = 7);
INSERT INTO org_class (org_id, department_id, enrollment_date, graduation_date)
SELECT 8, 3, '2025-09-01', '2029-06-30' WHERE NOT EXISTS (SELECT 1 FROM org_class WHERE org_id = 8);

-- ---------- 2. 40 名学生账号（2025309701 ~ 2025309740） ----------
INSERT INTO sys_user (account, mobile, password_hash, show_name, gender, coin, gift, guide_done)
SELECT t.acc, t.mob, '$2a$10$0sze7nVVJRWsLQYskoCfKuvUAs5XO2k90k1cEWFkyCNpXQ2VK2UQi', t.nm, 0, 100, 1, 1
FROM (
  SELECT '2025309701' acc, '13900000001' mob, '学生01' nm UNION ALL
  SELECT '2025309702','13900000002','学生02' UNION ALL
  SELECT '2025309703','13900000003','学生03' UNION ALL
  SELECT '2025309704','13900000004','学生04' UNION ALL
  SELECT '2025309705','13900000005','学生05' UNION ALL
  SELECT '2025309706','13900000006','学生06' UNION ALL
  SELECT '2025309707','13900000007','学生07' UNION ALL
  SELECT '2025309708','13900000008','学生08' UNION ALL
  SELECT '2025309709','13900000009','学生09' UNION ALL
  SELECT '2025309710','13900000010','学生10' UNION ALL
  SELECT '2025309711','13900000011','学生11' UNION ALL
  SELECT '2025309712','13900000012','学生12' UNION ALL
  SELECT '2025309713','13900000013','学生13' UNION ALL
  SELECT '2025309714','13900000014','学生14' UNION ALL
  SELECT '2025309715','13900000015','学生15' UNION ALL
  SELECT '2025309716','13900000016','学生16' UNION ALL
  SELECT '2025309717','13900000017','学生17' UNION ALL
  SELECT '2025309718','13900000018','学生18' UNION ALL
  SELECT '2025309719','13900000019','学生19' UNION ALL
  SELECT '2025309720','13900000020','学生20' UNION ALL
  SELECT '2025309721','13900000021','学生21' UNION ALL
  SELECT '2025309722','13900000022','学生22' UNION ALL
  SELECT '2025309723','13900000023','学生23' UNION ALL
  SELECT '2025309724','13900000024','学生24' UNION ALL
  SELECT '2025309725','13900000025','学生25' UNION ALL
  SELECT '2025309726','13900000026','学生26' UNION ALL
  SELECT '2025309727','13900000027','学生27' UNION ALL
  SELECT '2025309728','13900000028','学生28' UNION ALL
  SELECT '2025309729','13900000029','学生29' UNION ALL
  SELECT '2025309730','13900000030','学生30' UNION ALL
  SELECT '2025309731','13900000031','学生31' UNION ALL
  SELECT '2025309732','13900000032','学生32' UNION ALL
  SELECT '2025309733','13900000033','学生33' UNION ALL
  SELECT '2025309734','13900000034','学生34' UNION ALL
  SELECT '2025309735','13900000035','学生35' UNION ALL
  SELECT '2025309736','13900000036','学生36' UNION ALL
  SELECT '2025309737','13900000037','学生37' UNION ALL
  SELECT '2025309738','13900000038','学生38' UNION ALL
  SELECT '2025309739','13900000039','学生39' UNION ALL
  SELECT '2025309740','13900000040','学生40'
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.account = t.acc);

-- ---------- 3. 员工档案 ----------
INSERT INTO org_employee (user_id, name, employee_no, identity_type, identity_no, birthday, sex, root_org_id, hire_date, enable_relative)
SELECT u.id, u.show_name, u.account, 1,
       CONCAT('3501012005', LPAD(SUBSTR(u.account, 9), 4, '0'), '01'),
       '2006-09-01', 0, 1, '2025-09-01', 1
FROM sys_user u
WHERE u.account LIKE '20253097%'
  AND NOT EXISTS (SELECT 1 FROM org_employee e WHERE e.user_id = u.id);

-- ---------- 4. 学生 ↔ 班级（01-20 → 4 班；21-30 → 7 班；31-40 → 8 班） ----------
INSERT INTO org_employee_unit (employee_id, org_id, type_id, is_primary)
SELECT e.id,
       CASE
         WHEN CAST(SUBSTR(u.account, 9) AS UNSIGNED) <= 20 THEN 4
         WHEN CAST(SUBSTR(u.account, 9) AS UNSIGNED) <= 30 THEN 7
         ELSE 8
       END,
       CASE WHEN SUBSTR(u.account, 9) IN ('01', '21', '31') THEN 2 ELSE 1 END,
       1
FROM sys_user u
JOIN org_employee e ON e.user_id = u.id
WHERE u.account LIKE '20253097%'
  AND NOT EXISTS (SELECT 1 FROM org_employee_unit ou WHERE ou.employee_id = e.id);

INSERT INTO org_employee_unit (employee_id, org_id, type_id, is_primary)
SELECT e.id, 4, 1, 1
FROM sys_user u JOIN org_employee e ON e.user_id = u.id
WHERE u.account = '2025000001'
  AND NOT EXISTS (SELECT 1 FROM org_employee_unit ou WHERE ou.employee_id = e.id AND ou.org_id = 4);

-- ---------- 5. 角色：学生 + 每班首个为学委 ----------
INSERT INTO sys_user_role (user_id, role_code, org_id)
SELECT u.id, 'STUDENT',
       CASE
         WHEN CAST(SUBSTR(u.account, 9) AS UNSIGNED) <= 20 THEN 4
         WHEN CAST(SUBSTR(u.account, 9) AS UNSIGNED) <= 30 THEN 7
         ELSE 8
       END
FROM sys_user u
WHERE u.account LIKE '20253097%'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role r WHERE r.user_id = u.id AND r.role_code = 'STUDENT');

INSERT INTO sys_user_role (user_id, role_code, org_id)
SELECT u.id, 'MONITOR',
       CASE WHEN CAST(SUBSTR(u.account, 9) AS UNSIGNED) <= 20 THEN 4
            WHEN CAST(SUBSTR(u.account, 9) AS UNSIGNED) <= 30 THEN 7 ELSE 8 END
FROM sys_user u
WHERE u.account LIKE '20253097%'
  AND SUBSTR(u.account, 9) IN ('01', '21', '31')
  AND NOT EXISTS (SELECT 1 FROM sys_user_role r WHERE r.user_id = u.id AND r.role_code = 'MONITOR');

-- ---------- 6. 另两位辅导员 + 一位书记 ----------
INSERT INTO sys_user (account, mobile, password_hash, show_name, gender, guide_done)
SELECT '1002001', '13800138002', '$2a$10$0sze7nVVJRWsLQYskoCfKuvUAs5XO2k90k1cEWFkyCNpXQ2VK2UQi', '辅导员李老师', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE account = '1002001');
INSERT INTO sys_user (account, mobile, password_hash, show_name, gender, guide_done)
SELECT '1003001', '13800138003', '$2a$10$0sze7nVVJRWsLQYskoCfKuvUAs5XO2k90k1cEWFkyCNpXQ2VK2UQi', '学院书记陈老师', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE account = '1003001');

INSERT INTO org_employee (user_id, name, employee_no, identity_type, identity_no, sex, root_org_id, hire_date, enable_relative)
SELECT u.id, u.show_name, u.account, 1, CONCAT('3501011980010', RIGHT(u.account, 3)), 1, 1, '2020-09-01', 0
FROM sys_user u
WHERE u.account IN ('1002001', '1003001')
  AND NOT EXISTS (SELECT 1 FROM org_employee e WHERE e.user_id = u.id);

INSERT INTO org_employee_unit (employee_id, org_id, type_id, is_primary)
SELECT e.id, CASE WHEN u.account = '1002001' THEN 5 ELSE 2 END, CASE WHEN u.account = '1002001' THEN 4 ELSE 5 END, 1
FROM sys_user u JOIN org_employee e ON e.user_id = u.id
WHERE u.account IN ('1002001', '1003001')
  AND NOT EXISTS (SELECT 1 FROM org_employee_unit ou WHERE ou.employee_id = e.id);

INSERT INTO sys_user_role (user_id, role_code, org_id)
SELECT u.id, CASE WHEN u.account = '1002001' THEN 'COUNSELOR' ELSE 'SECRETARY' END, 2
FROM sys_user u
WHERE u.account IN ('1002001', '1003001')
  AND NOT EXISTS (SELECT 1 FROM sys_user_role r WHERE r.user_id = u.id);

UPDATE org_class SET counselor_id = (SELECT e.id FROM sys_user u JOIN org_employee e ON e.user_id = u.id WHERE u.account = '1002001')
WHERE org_id IN (7, 8) AND counselor_id IS NULL;

-- ---------- 6b. 家长账号（用于家校联动演示） ----------
INSERT INTO sys_user (account, mobile, password_hash, show_name, gender, guide_done)
SELECT '13900001111', '13900001111', '$2a$10$0sze7nVVJRWsLQYskoCfKuvUAs5XO2k90k1cEWFkyCNpXQ2VK2UQi', '家长杨先生', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE account = '13900001111');

INSERT INTO sys_user_role (user_id, role_code, org_id)
SELECT u.id, 'PARENT', NULL FROM sys_user u
WHERE u.account = '13900001111'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role r WHERE r.user_id = u.id AND r.role_code = 'PARENT');

-- ---------- 7. 请假样例（覆盖全部状态） ----------
INSERT INTO leave_request (employee_id, college_org_id, class_org_id, type, start_time, end_time, days, reason, status, current_step)
SELECT e.id, 2, 4, 1, '2026-11-02 08:00:00', '2026-11-04 18:00:00', 3.0, '发热就诊，附诊断证明', 10, 1
FROM sys_user u JOIN org_employee e ON e.user_id = u.id
WHERE u.account = '2025309702'
  AND NOT EXISTS (SELECT 1 FROM leave_request l WHERE l.employee_id = e.id AND l.start_time = '2026-11-02 08:00:00');

INSERT INTO leave_request (employee_id, college_org_id, class_org_id, type, start_time, end_time, days, reason, status, current_step)
SELECT e.id, 2, 4, 2, '2026-11-05 08:00:00', '2026-11-09 18:00:00', 5.0, '家中有事需返乡', 11, 2
FROM sys_user u JOIN org_employee e ON e.user_id = u.id
WHERE u.account = '2025309703'
  AND NOT EXISTS (SELECT 1 FROM leave_request l WHERE l.employee_id = e.id AND l.start_time = '2026-11-05 08:00:00');

INSERT INTO leave_request (employee_id, college_org_id, class_org_id, type, start_time, end_time, days, reason, status, current_step)
SELECT e.id, 2, 4, 1, '2026-10-20 08:00:00', '2026-10-22 18:00:00', 3.0, '肠胃炎住院', 20, 1
FROM sys_user u JOIN org_employee e ON e.user_id = u.id
WHERE u.account = '2025309704'
  AND NOT EXISTS (SELECT 1 FROM leave_request l WHERE l.employee_id = e.id AND l.start_time = '2026-10-20 08:00:00');

INSERT INTO leave_request (employee_id, college_org_id, class_org_id, type, start_time, end_time, days, reason, status, current_step, return_time)
SELECT e.id, 2, 4, 2, '2026-10-10 08:00:00', '2026-10-12 18:00:00', 3.0, '亲属婚礼', 21, 1, '2026-10-12 17:00:00'
FROM sys_user u JOIN org_employee e ON e.user_id = u.id
WHERE u.account = '2025309705'
  AND NOT EXISTS (SELECT 1 FROM leave_request l WHERE l.employee_id = e.id AND l.start_time = '2026-10-10 08:00:00');

INSERT INTO leave_request (employee_id, college_org_id, class_org_id, type, start_time, end_time, days, reason, status, current_step)
SELECT e.id, 2, 4, 1, '2026-10-25 08:00:00', '2026-10-26 18:00:00', 2.0, '牙科手术', 30, 1
FROM sys_user u JOIN org_employee e ON e.user_id = u.id
WHERE u.account = '2025309706'
  AND NOT EXISTS (SELECT 1 FROM leave_request l WHERE l.employee_id = e.id AND l.start_time = '2026-10-25 08:00:00');

INSERT INTO leave_request (employee_id, college_org_id, class_org_id, type, start_time, end_time, days, reason, status, current_step)
SELECT e.id, 2, 4, 1, '2026-09-28 08:00:00', '2026-09-30 18:00:00', 3.0, '感冒发烧', 50, 1
FROM sys_user u JOIN org_employee e ON e.user_id = u.id
WHERE u.account = '2025309707'
  AND NOT EXISTS (SELECT 1 FROM leave_request l WHERE l.employee_id = e.id AND l.start_time = '2026-09-28 08:00:00');

-- 审批节点：为已提交的假条补一条待处理节点
INSERT INTO leave_approve (leave_id, step, approver_role, approver_id, approver_name, result, status)
SELECT l.id, 1, 'COUNSELOR',
       (SELECT id FROM org_employee WHERE employee_no = '1001001' LIMIT 1), '辅导员王老师', 0, 0
FROM leave_request l
WHERE l.status IN (10, 11)
  AND NOT EXISTS (SELECT 1 FROM leave_approve a WHERE a.leave_id = l.id);

-- 状态 11（待书记）的假条：第一级已通过，补第二级节点
UPDATE leave_approve a
JOIN leave_request l ON l.id = a.leave_id
SET a.result = 1, a.status = 1, a.handle_time = NOW()
WHERE l.status = 11 AND a.step = 1 AND a.result = 0 AND a.status = 0;

INSERT INTO leave_approve (leave_id, step, approver_role, approver_id, approver_name, result, status)
SELECT l.id, 2, 'SECRETARY',
       (SELECT id FROM org_employee WHERE employee_no = '1003001' LIMIT 1), '学院书记陈老师', 0, 0
FROM leave_request l
WHERE l.status = 11
  AND NOT EXISTS (SELECT 1 FROM leave_approve a WHERE a.leave_id = l.id AND a.step = 2);

-- ---------- 8. 新闻 / 广告 / 红黑榜 ----------
INSERT INTO pub_news (title, sub_title, news_type, cover_image, visible_org_id, start_time, end_time, status, publisher_id)
SELECT '关于开展 2026 年秋季校园安全教育周的通知', '全员参与，含消防演练', 1, '/static/news/1.jpg', 1,
       '2026-10-20 00:00:00', '2026-11-30 23:59:59', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM pub_news WHERE title LIKE '%校园安全教育周%');

INSERT INTO pub_news (title, sub_title, news_type, visible_org_id, start_time, end_time, status, publisher_id)
SELECT '信息工程学院第七届程序设计大赛开始报名', '面向全院 2025 级', 2, 2,
       '2026-10-25 00:00:00', '2026-11-25 23:59:59', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM pub_news WHERE title LIKE '%程序设计大赛%');

INSERT INTO pub_news (title, news_type, visible_org_id, start_time, end_time, status, publisher_id)
SELECT '软工2025级1班期中教学座谈会纪要', 3, 4,
       '2026-10-28 00:00:00', '2026-12-31 23:59:59', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM pub_news WHERE title LIKE '%座谈会纪要%');

INSERT INTO pub_news_content (news_id, content)
SELECT n.id, CONCAT('<h2>', n.title, '</h2><p>这里是正文占位内容，后台可用富文本编辑器替换。</p>')
FROM pub_news n
WHERE NOT EXISTS (SELECT 1 FROM pub_news_content c WHERE c.news_id = n.id);

INSERT INTO pub_ad (title, sub_title, image, link_type, link, start_time, end_time, status, visible_org_id, sort)
SELECT '2026 校园招聘季', '名企进校宣讲', '/static/ad/1.jpg', 2, 'https://example.com', 
       '2026-10-01 00:00:00', '2026-12-31 23:59:59', 1, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM pub_ad WHERE title LIKE '%校园招聘季%');

INSERT INTO pub_ad (title, image, link_type, link, start_time, end_time, status, visible_org_id, sort)
SELECT '图书馆座位预约上线', '/static/ad/2.jpg', 1, '/news',
       '2026-10-10 00:00:00', '2026-12-31 23:59:59', 1, 1, 2
WHERE NOT EXISTS (SELECT 1 FROM pub_ad WHERE title LIKE '%座位预约%');

INSERT INTO pub_rank (employee_id, title, sub_title, red, label_id, type, achievement, update_time, status, relative_visible, publisher_id)
SELECT e.id, '程序设计大赛校赛一等奖', '2026 年校赛', 1, 3, 1, '代表班级参赛并获一等奖', '2026-10-26 10:00:00', 1, 1, 1
FROM sys_user u JOIN org_employee e ON e.user_id = u.id
WHERE u.account = '2025000001'
  AND NOT EXISTS (SELECT 1 FROM pub_rank r WHERE r.employee_id = e.id);

INSERT INTO pub_rank (employee_id, title, sub_title, red, label_id, type, achievement, update_time, status, relative_visible, publisher_id)
SELECT e.id, '本学期全勤', '无迟到早退', 1, 4, 2, '连续 8 周全勤', '2026-10-20 10:00:00', 1, 1, 1
FROM sys_user u JOIN org_employee e ON e.user_id = u.id
WHERE u.account = '2025309702'
  AND NOT EXISTS (SELECT 1 FROM pub_rank r WHERE r.employee_id = e.id AND r.title = '本学期全勤');

INSERT INTO pub_rank (employee_id, title, sub_title, red, label_id, type, achievement, update_time, status, relative_visible, publisher_id)
SELECT e.id, '旷课 4 节', '第 7-8 周', 0, 7, 5, '未履行请假手续', '2026-10-18 10:00:00', 1, 0, 1
FROM sys_user u JOIN org_employee e ON e.user_id = u.id
WHERE u.account = '2025309709'
  AND NOT EXISTS (SELECT 1 FROM pub_rank r WHERE r.employee_id = e.id AND r.title = '旷课 4 节');

INSERT INTO pub_rank (employee_id, title, red, label_id, type, update_time, status, relative_visible, publisher_id)
SELECT e.id, '晚归 2 次', 0, 5, 5, '2026-10-22 10:00:00', 1, 1, 1
FROM sys_user u JOIN org_employee e ON e.user_id = u.id
WHERE u.account = '2025309712'
  AND NOT EXISTS (SELECT 1 FROM pub_rank r WHERE r.employee_id = e.id AND r.title = '晚归 2 次');

INSERT INTO pub_rank_content (rank_id, content)
SELECT r.id, CONCAT('<p>', r.title, '：详情占位，可在后台补录具体事迹。</p>')
FROM pub_rank r
WHERE NOT EXISTS (SELECT 1 FROM pub_rank_content c WHERE c.rank_id = r.id);

-- ---------- 9. 课表（软工2025级1班 / 2026-2027-1） ----------
INSERT INTO edu_course (course_code, name, teacher_name, credit, class_org_id, term)
SELECT t.code, t.name, t.teacher, t.credit, 4, '2026-2027-1'
FROM (
  SELECT 'CS101' code, '数据结构' name, '张老师' teacher, 4.0 credit UNION ALL
  SELECT 'CS102','计算机网络','李老师',3.0 UNION ALL
  SELECT 'CS103','Spring 微服务','王老师',3.0 UNION ALL
  SELECT 'CS104','数据库原理','赵老师',3.5 UNION ALL
  SELECT 'PE101','体育（篮球）','孙老师',1.0
) t
WHERE NOT EXISTS (SELECT 1 FROM edu_course c WHERE c.course_code = t.code AND c.term = '2026-2027-1');

INSERT INTO edu_timetable (course_id, class_org_id, term, week_day, section_start, section_end, location, weeks)
SELECT c.id, 4, '2026-2027-1', t.wd, t.ss, t.se, t.loc, '1-16'
FROM (
  SELECT 'CS101' code, 1 wd, 1 ss, 2 se, '教学楼A201' loc UNION ALL
  SELECT 'CS102', 1, 3, 4, '教学楼A305' UNION ALL
  SELECT 'CS103', 2, 1, 2, '实验楼B402' UNION ALL
  SELECT 'CS104', 3, 3, 4, '教学楼A210' UNION ALL
  SELECT 'CS103', 3, 5, 6, '实验楼B402' UNION ALL
  SELECT 'PE101', 4, 3, 4, '体育馆' UNION ALL
  SELECT 'CS101', 5, 1, 2, '教学楼A201' UNION ALL
  SELECT 'CS104', 5, 5, 6, '教学楼A210'
) t
JOIN edu_course c ON c.course_code = t.code AND c.term = '2026-2027-1'
WHERE NOT EXISTS (
  SELECT 1 FROM edu_timetable tt WHERE tt.course_id = c.id AND tt.week_day = t.wd AND tt.section_start = t.ss
);

-- ---------- 11. 奖学金计划（报名中） ----------
INSERT INTO sch_plan (name, org_id, term, quota, amount, apply_start, apply_end, rule_json, status)
SELECT '2026 年国家励志奖学金（信息工程学院）', 2, '2026-2027-1', 3, 5000.00,
       '2026-10-01 00:00:00', '2027-06-30 23:59:59',
       '{"gpaWeight":6,"redWeight":2,"blackWeight":3}', 1
WHERE NOT EXISTS (SELECT 1 FROM sch_plan WHERE name LIKE '%国家励志奖学金%');

SELECT '演示数据完成' AS msg,
       (SELECT COUNT(*) FROM sys_user) AS users,
       (SELECT COUNT(*) FROM org_employee) AS employees,
       (SELECT COUNT(*) FROM leave_request) AS leaves,
       (SELECT COUNT(*) FROM pub_news) AS news,
       (SELECT COUNT(*) FROM pub_rank) AS ranks,
       (SELECT COUNT(*) FROM edu_timetable) AS timetable;
