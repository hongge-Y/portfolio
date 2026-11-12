-- ============================================================
-- 演示用站内通知（消息中心）复位脚本
--
-- 用途：冒烟/评测跑多了以后，消息中心会堆几十条"新的请假申请"，
--       演示时不好看。这个脚本把通知清空后重铺一套能讲的通知：
--       每个角色 2-3 条，时间错开，标题与业务对得上。
--
-- 执行：mysql ... portfolio < scripts/seed-demo-messages.sql
--       （reset-demo.sh --messages 会连带调用它）
--
-- 只动 msg_notification / msg_receive 两张表，不碰业务数据。
-- ============================================================

UPDATE msg_notification SET deleted = 1 WHERE deleted = 0;
DELETE FROM msg_receive;

-- ---------- 学生：林同学（2025000001）----------
INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids, create_time)
SELECT 1, NULL, '请假已通过', 1, '<p>你提交的 3 天病假申请已通过辅导员与学院审批，请按时返校并在系统内销假。</p>', 1,
       DATE_SUB(NOW(), INTERVAL 2 DAY), CAST(u.id AS CHAR), DATE_SUB(NOW(), INTERVAL 2 DAY)
FROM sys_user u WHERE u.account = '2025000001';
SET @n = LAST_INSERT_ID();
INSERT IGNORE INTO msg_receive (notification_id, to_user_id) SELECT @n, id FROM sys_user WHERE account = '2025000001';

INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids, create_time)
SELECT 1, NULL, '奖学金评审结果', 1, '<p>你申请的"学业优秀奖学金"已通过学院初审，材料完整，等待学校终审公示。</p>', 1,
       DATE_SUB(NOW(), INTERVAL 5 DAY), CAST(u.id AS CHAR), DATE_SUB(NOW(), INTERVAL 5 DAY)
FROM sys_user u WHERE u.account = '2025000001';
SET @n = LAST_INSERT_ID();
INSERT IGNORE INTO msg_receive (notification_id, to_user_id) SELECT @n, id FROM sys_user WHERE account = '2025000001';

INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids, create_time)
SELECT 1, NULL, '课表调整提醒', 0, '<p>本周四《数据结构》实验课调整到实验楼 A305，请以课表页最新安排为准。</p>', 1,
       DATE_SUB(NOW(), INTERVAL 1 DAY), CAST(u.id AS CHAR), DATE_SUB(NOW(), INTERVAL 1 DAY)
FROM sys_user u WHERE u.account = '2025000001';
SET @n = LAST_INSERT_ID();
INSERT IGNORE INTO msg_receive (notification_id, to_user_id) SELECT @n, id FROM sys_user WHERE account = '2025000001';

INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids, create_time)
SELECT 1, NULL, '冬季作息时间调整', 0, '<p>11 月 15 日起执行冬季作息，第一节上课时间调整为 8:00，请提前 10 分钟到课。</p>', 1,
       DATE_SUB(NOW(), INTERVAL 7 DAY), CAST(u.id AS CHAR), DATE_SUB(NOW(), INTERVAL 7 DAY)
FROM sys_user u WHERE u.account = '2025000001';
SET @n = LAST_INSERT_ID();
INSERT IGNORE INTO msg_receive (notification_id, to_user_id) SELECT @n, id FROM sys_user WHERE account = '2025000001';

-- ---------- 辅导员：王老师（1001001）----------
INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids, create_time)
SELECT 1, NULL, '新的请假申请', 1, '<p>林同学 提交了 3 天病假（附住院证明），请及时审批。</p>', 1,
       DATE_SUB(NOW(), INTERVAL 3 HOUR), CAST(u.id AS CHAR), DATE_SUB(NOW(), INTERVAL 3 HOUR)
FROM sys_user u WHERE u.account = '1001001';
SET @n = LAST_INSERT_ID();
INSERT IGNORE INTO msg_receive (notification_id, to_user_id) SELECT @n, id FROM sys_user WHERE account = '1001001';

INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids, create_time)
SELECT 1, NULL, '学生销假待确认', 0, '<p>林同学 已返校并提交销假，请核对返校时间后确认。</p>', 1,
       DATE_SUB(NOW(), INTERVAL 1 DAY), CAST(u.id AS CHAR), DATE_SUB(NOW(), INTERVAL 1 DAY)
FROM sys_user u WHERE u.account = '1001001';
SET @n = LAST_INSERT_ID();
INSERT IGNORE INTO msg_receive (notification_id, to_user_id) SELECT @n, id FROM sys_user WHERE account = '1001001';

INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids, create_time)
SELECT 1, NULL, '助学金材料待审', 0, '<p>本班 3 份助学金申请材料已提交，需在 11 月 20 日前完成初审。</p>', 1,
       DATE_SUB(NOW(), INTERVAL 3 DAY), CAST(u.id AS CHAR), DATE_SUB(NOW(), INTERVAL 3 DAY)
FROM sys_user u WHERE u.account = '1001001';
SET @n = LAST_INSERT_ID();
INSERT IGNORE INTO msg_receive (notification_id, to_user_id) SELECT @n, id FROM sys_user WHERE account = '1001001';

-- ---------- 学院书记：陈老师（1003001）----------
INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids, create_time)
SELECT 1, NULL, '超 7 天请假待终审', 2, '<p>软件工程2025级1班 1 条请假已超 7 天，辅导员已通过，待学院终审。</p>', 1,
       DATE_SUB(NOW(), INTERVAL 6 HOUR), CAST(u.id AS CHAR), DATE_SUB(NOW(), INTERVAL 6 HOUR)
FROM sys_user u WHERE u.account = '1003001';
SET @n = LAST_INSERT_ID();
INSERT IGNORE INTO msg_receive (notification_id, to_user_id) SELECT @n, id FROM sys_user WHERE account = '1003001';

INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids, create_time)
SELECT 1, NULL, '奖学金终审提醒', 1, '<p>本学院 5 份奖学金申请待终审，请在公示前完成审批。</p>', 1,
       DATE_SUB(NOW(), INTERVAL 2 DAY), CAST(u.id AS CHAR), DATE_SUB(NOW(), INTERVAL 2 DAY)
FROM sys_user u WHERE u.account = '1003001';
SET @n = LAST_INSERT_ID();
INSERT IGNORE INTO msg_receive (notification_id, to_user_id) SELECT @n, id FROM sys_user WHERE account = '1003001';

-- ---------- 家长：陈先生（13900001111）----------
INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids, create_time)
SELECT 1, NULL, '孩子请假已批准', 1, '<p>你关注的 林同学 的 3 天病假申请已通过审批，返校后系统会通知你。</p>', 1,
       DATE_SUB(NOW(), INTERVAL 1 DAY), CAST(u.id AS CHAR), DATE_SUB(NOW(), INTERVAL 1 DAY)
FROM sys_user u WHERE u.account = '13900001111';
SET @n = LAST_INSERT_ID();
INSERT IGNORE INTO msg_receive (notification_id, to_user_id) SELECT @n, id FROM sys_user WHERE account = '13900001111';

INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids, create_time)
SELECT 1, NULL, '家校关联已生效', 0, '<p>你与 林同学 的家长关联已确认，可查看请假与成绩提醒。</p>', 1,
       DATE_SUB(NOW(), INTERVAL 6 DAY), CAST(u.id AS CHAR), DATE_SUB(NOW(), INTERVAL 6 DAY)
FROM sys_user u WHERE u.account = '13900001111';
SET @n = LAST_INSERT_ID();
INSERT IGNORE INTO msg_receive (notification_id, to_user_id) SELECT @n, id FROM sys_user WHERE account = '13900001111';

-- ---------- 管理员：admin ----------
INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids, create_time)
SELECT 1, NULL, '账号权限变更提醒', 1, '<p>系统管理员修改了 1 个账号的角色，变更已记入审计日志。</p>', 1,
       DATE_SUB(NOW(), INTERVAL 4 DAY), CAST(u.id AS CHAR), DATE_SUB(NOW(), INTERVAL 4 DAY)
FROM sys_user u WHERE u.account = 'admin';
SET @n = LAST_INSERT_ID();
INSERT IGNORE INTO msg_receive (notification_id, to_user_id) SELECT @n, id FROM sys_user WHERE account = 'admin';

INSERT INTO msg_notification (type, sender_id, title, level, content, status, send_time, to_user_ids, create_time)
SELECT 1, NULL, '本周巡检完成', 0, '<p>接口成功率 100%，慢查询 0 条，数据库备份正常。</p>', 1,
       DATE_SUB(NOW(), INTERVAL 5 HOUR), CAST(u.id AS CHAR), DATE_SUB(NOW(), INTERVAL 5 HOUR)
FROM sys_user u WHERE u.account = 'admin';
SET @n = LAST_INSERT_ID();
INSERT IGNORE INTO msg_receive (notification_id, to_user_id) SELECT @n, id FROM sys_user WHERE account = 'admin';

SELECT u.account, COUNT(r.id) AS msgs
FROM sys_user u LEFT JOIN msg_receive r ON r.to_user_id = u.id
WHERE u.account IN ('2025000001','1001001','1003001','admin','13900001111')
GROUP BY u.account;
