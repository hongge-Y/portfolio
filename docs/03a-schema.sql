-- =============================================================
-- Portfolio 校园成长平台 · 数据库建表脚本
-- 版本：V2.0   目标库：MySQL 8.0+（最低 5.6.5，因使用 DATETIME DEFAULT CURRENT_TIMESTAMP）
--              注意：MariaDB 5.5 / MySQL 5.5 会报 ERROR 1067，不要用
-- 字符集：utf8mb4
-- 说明：本脚本为唯一权威结构定义，文档 03-数据库设计.md 与之对应。
--      ID 统一 BIGINT 自增；金额 DECIMAL；时间 DATETIME；
--      软删除统一 deleted TINYINT，审计统一 create_*/update_*。
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS portfolio
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE portfolio;

-- -------------------------------------------------------------
-- 一、账号与权限
-- -------------------------------------------------------------

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  account         VARCHAR(64)     NOT NULL                COMMENT '登录账号',
  mobile          VARCHAR(20)     DEFAULT NULL            COMMENT '手机号',
  mail            VARCHAR(128)    DEFAULT NULL            COMMENT '邮箱',
  password_hash   VARCHAR(128)    DEFAULT NULL            COMMENT 'BCrypt 密码，NULL=未设置',
  avatar          VARCHAR(255)    DEFAULT '/avatar/default.svg' COMMENT '头像',
  show_name       VARCHAR(64)     NOT NULL                COMMENT '展示名(列表/导航)',
  nick_name       VARCHAR(64)     DEFAULT NULL            COMMENT '昵称',
  birthday        DATE            DEFAULT NULL            COMMENT '生日',
  gender          TINYINT         NOT NULL DEFAULT 2      COMMENT '0男 1女 2保密',
  coin            INT             NOT NULL DEFAULT 0      COMMENT '光粒',
  gift            INT             NOT NULL DEFAULT 0      COMMENT '礼盒数',
  is_vip          TINYINT         NOT NULL DEFAULT 0      COMMENT '是否VIP',
  pet             TINYINT         NOT NULL DEFAULT 0      COMMENT '宠物 0未选 1星光猫 2云朵犬 3萤火兔 4树莓熊 5月影狐',
  pet_hide        TINYINT         NOT NULL DEFAULT 0      COMMENT '宠物是否隐藏',
  guide_done      TINYINT         NOT NULL DEFAULT 0      COMMENT '新手引导是否完成',
  status          TINYINT         NOT NULL DEFAULT 1      COMMENT '1正常 0禁用',
  last_login_time DATETIME        DEFAULT NULL,
  last_login_ip   VARCHAR(64)     DEFAULT NULL,
  create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_account (account),
  UNIQUE KEY uk_mobile (mobile),
  UNIQUE KEY uk_mail (mail),
  KEY idx_show_name (show_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号';

DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code        VARCHAR(32)  NOT NULL COMMENT 'STUDENT/MONITOR/COUNSELOR/SECRETARY/TEACHER/PARENT/ADMIN',
  name        VARCHAR(32)  NOT NULL,
  remark      VARCHAR(128) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
  id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id   BIGINT UNSIGNED NOT NULL,
  role_code VARCHAR(32)     NOT NULL,
  org_id    BIGINT UNSIGNED DEFAULT NULL COMMENT '角色作用域组织，NULL=全局',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role_org (user_id, role_code, org_id),
  KEY idx_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色(含作用域)';

DROP TABLE IF EXISTS sys_third_account;
CREATE TABLE sys_third_account (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  provider     VARCHAR(16)  NOT NULL COMMENT 'weixin/weibo/alipay',
  open_id      VARCHAR(128) NOT NULL,
  union_id     VARCHAR(128) DEFAULT NULL,
  nick_name    VARCHAR(64)  DEFAULT NULL,
  bind_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_provider_openid (provider, open_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方账号绑定';

DROP TABLE IF EXISTS sys_login_log;
CREATE TABLE sys_login_log (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED DEFAULT NULL,
  account    VARCHAR(64)   DEFAULT NULL,
  login_type VARCHAR(16)   NOT NULL COMMENT 'MOBILE/MAIL/PASSWORD/THIRD',
  ip         VARCHAR(64)   DEFAULT NULL,
  ua         VARCHAR(255)  DEFAULT NULL,
  success    TINYINT       NOT NULL DEFAULT 1,
  msg        VARCHAR(255)  DEFAULT NULL,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

-- -------------------------------------------------------------
-- 二、组织架构
-- -------------------------------------------------------------

DROP TABLE IF EXISTS org_unit;
CREATE TABLE org_unit (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name        VARCHAR(128) NOT NULL,
  type        TINYINT      NOT NULL COMMENT '1学校 2学院 3系/专业 4班级',
  parent_id   BIGINT UNSIGNED DEFAULT NULL,
  path        VARCHAR(255) NOT NULL DEFAULT '/' COMMENT '祖先路径 /1/2/5/ 便于子树查询',
  sort        INT          NOT NULL DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 1,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_parent (parent_id),
  KEY idx_path (path),
  KEY idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织单元(学校/学院/系/班)';

DROP TABLE IF EXISTS org_college;
CREATE TABLE org_college (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  org_id         BIGINT UNSIGNED NOT NULL COMMENT '指向 org_unit(type=2)',
  major_count    INT          NOT NULL DEFAULT 0,
  master_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '院长 employee_id',
  party_secretary_id BIGINT UNSIGNED DEFAULT NULL COMMENT '书记 employee_id',
  position       VARCHAR(255) DEFAULT NULL,
  description    TEXT,
  PRIMARY KEY (id),
  UNIQUE KEY uk_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学院扩展信息';

DROP TABLE IF EXISTS org_class;
CREATE TABLE org_class (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  org_id          BIGINT UNSIGNED NOT NULL COMMENT '指向 org_unit(type=4)',
  department_id   BIGINT UNSIGNED DEFAULT NULL COMMENT '所属系/专业 org_unit',
  adviser_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '班主任 employee_id',
  counselor_id    BIGINT UNSIGNED DEFAULT NULL COMMENT '辅导员 employee_id',
  monitor_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '班长 employee_id',
  enrollment_date DATE DEFAULT NULL,
  graduation_date DATE DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_org (org_id),
  KEY idx_counselor (counselor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级扩展信息';

DROP TABLE IF EXISTS org_employee_type;
CREATE TABLE org_employee_type (
  id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(32) NOT NULL COMMENT '学生/学委/教师/辅导员/书记/行政',
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工类型';

DROP TABLE IF EXISTS org_position;
CREATE TABLE org_position (
  id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name  VARCHAR(32) NOT NULL COMMENT '院长/校长/班长/学委',
  level INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职位';

DROP TABLE IF EXISTS org_employee;
CREATE TABLE org_employee (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id        BIGINT UNSIGNED NOT NULL,
  name           VARCHAR(64)  NOT NULL,
  employee_no    VARCHAR(32)  NOT NULL COMMENT '学号/工号',
  identity_type  TINYINT      NOT NULL DEFAULT 1 COMMENT '1身份证 2护照 3军人证 4港澳台',
  identity_no    VARCHAR(32)  NOT NULL,
  birthday       DATE DEFAULT NULL,
  sex            TINYINT      NOT NULL DEFAULT 0 COMMENT '0男 1女 2其他',
  root_org_id    BIGINT UNSIGNED NOT NULL COMMENT '所属学校 org_unit',
  hire_date      DATE DEFAULT NULL COMMENT '入学/入职日期',
  enable_relative TINYINT     NOT NULL DEFAULT 1 COMMENT '0不允许被家长关联 1允许',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user (user_id),
  UNIQUE KEY uk_no (root_org_id, employee_no),
  KEY idx_name (name),
  KEY idx_identity (identity_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工/学生档案';

DROP TABLE IF EXISTS org_employee_unit;
CREATE TABLE org_employee_unit (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  employee_id BIGINT UNSIGNED NOT NULL,
  org_id      BIGINT UNSIGNED NOT NULL,
  type_id     BIGINT UNSIGNED NOT NULL COMMENT '员工类型(学生/教师/辅导员…)',
  position_id BIGINT UNSIGNED DEFAULT NULL,
  is_primary  TINYINT NOT NULL DEFAULT 1 COMMENT '主属组织',
  PRIMARY KEY (id),
  UNIQUE KEY uk_emp_org (employee_id, org_id, type_id),
  KEY idx_org (org_id),
  KEY idx_type (type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工-组织关系';

-- -------------------------------------------------------------
-- 三、家长关联
-- -------------------------------------------------------------

DROP TABLE IF EXISTS rel_relative_link;
CREATE TABLE rel_relative_link (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  parent_user_id   BIGINT UNSIGNED NOT NULL COMMENT '家长用户ID',
  employee_id      BIGINT UNSIGNED NOT NULL COMMENT '学生 employee_id',
  apply_from       VARCHAR(8) NOT NULL COMMENT 'PARENT=家长申请 STUDENT=学生申请',
  status           TINYINT NOT NULL DEFAULT 0 COMMENT '0待确认 1已关联 2已拒绝 3已解除',
  remark           VARCHAR(255) DEFAULT NULL,
  apply_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handle_time      DATETIME DEFAULT NULL,
  handle_user_id   BIGINT UNSIGNED DEFAULT NULL,
  create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pair (parent_user_id, employee_id),
  KEY idx_employee (employee_id, status),
  KEY idx_parent (parent_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长-学生关联';

-- -------------------------------------------------------------
-- 四、请假
-- -------------------------------------------------------------

DROP TABLE IF EXISTS leave_request;
CREATE TABLE leave_request (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  employee_id    BIGINT UNSIGNED NOT NULL,
  college_org_id BIGINT UNSIGNED NOT NULL,
  class_org_id   BIGINT UNSIGNED NOT NULL,
  type           TINYINT  NOT NULL COMMENT '1病假 2事假',
  start_time     DATETIME NOT NULL,
  end_time       DATETIME NOT NULL,
  days           DECIMAL(4,1) NOT NULL COMMENT '请假天数(按自然日折算)',
  reason         VARCHAR(500) NOT NULL,
  status         SMALLINT NOT NULL DEFAULT 10
                 COMMENT '10待辅导员 11待书记 20已通过待销假 21已销假 30已驳回 40已取消 50超期未销假',
  current_step   TINYINT  NOT NULL DEFAULT 1,
  return_time    DATETIME DEFAULT NULL COMMENT '实际销假时间',
  overdue_warn   TINYINT  NOT NULL DEFAULT 0 COMMENT '是否已发超期提醒',
  version        INT      NOT NULL DEFAULT 0 COMMENT '乐观锁',
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        TINYINT  NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_emp_status (employee_id, status),
  KEY idx_class_status (class_org_id, status),
  KEY idx_time (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请假申请';

DROP TABLE IF EXISTS leave_attachment;
CREATE TABLE leave_attachment (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  leave_id   BIGINT UNSIGNED NOT NULL,
  file_url   VARCHAR(255) NOT NULL,
  file_name  VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_leave (leave_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请假附件';

DROP TABLE IF EXISTS leave_approve;
CREATE TABLE leave_approve (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  leave_id      BIGINT UNSIGNED NOT NULL,
  step          TINYINT NOT NULL COMMENT '1辅导员 2书记',
  approver_role VARCHAR(32) NOT NULL COMMENT 'COUNSELOR/SECRETARY',
  approver_id   BIGINT UNSIGNED DEFAULT NULL COMMENT '实际审批人员工ID',
  approver_name VARCHAR(64) DEFAULT NULL,
  result        TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1批准 2驳回',
  reason        VARCHAR(255) DEFAULT NULL,
  status        TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已处理 2已跳过',
  handle_time   DATETIME DEFAULT NULL,
  create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_leave (leave_id),
  KEY idx_approver (approver_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请假审批节点';

DROP TABLE IF EXISTS leave_share;
CREATE TABLE leave_share (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  class_org_id      BIGINT UNSIGNED NOT NULL,
  leave_ids         VARCHAR(500) NOT NULL COMMENT '逗号分隔的请假ID',
  snapshot_json     JSON NOT NULL COMMENT '分享快照(脱敏：无理由/无附件)',
  teacher_employee_id BIGINT UNSIGNED DEFAULT NULL COMMENT '指定授课老师',
  share_token       VARCHAR(64) NOT NULL COMMENT '免登录访问令牌',
  expire_time       DATETIME NOT NULL,
  share_by          BIGINT UNSIGNED NOT NULL COMMENT '学委 employee_id',
  create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_token (share_token),
  KEY idx_class (class_org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级请假分享给授课老师';

-- -------------------------------------------------------------
-- 五、站内消息
-- -------------------------------------------------------------

DROP TABLE IF EXISTS msg_notification;
CREATE TABLE msg_notification (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  type        TINYINT NOT NULL DEFAULT 2 COMMENT '1系统 2指定人 3联盟 4指导老师通知',
  sender_id   BIGINT UNSIGNED DEFAULT NULL,
  title       VARCHAR(200) NOT NULL,
  level       TINYINT NOT NULL DEFAULT 0 COMMENT '0普通 1紧急 2警告 3致命',
  content     MEDIUMTEXT COMMENT 'HTML 正文',
  status      TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布',
  send_time   DATETIME DEFAULT NULL,
  to_user_ids VARCHAR(1000) DEFAULT NULL COMMENT '冗余的收件人快照',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_sender (sender_id),
  KEY idx_status_time (status, send_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息主体';

DROP TABLE IF EXISTS msg_receive;
CREATE TABLE msg_receive (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  notification_id BIGINT UNSIGNED NOT NULL,
  to_user_id      BIGINT UNSIGNED NOT NULL,
  read_status     TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  read_time       DATETIME DEFAULT NULL,
  deleted         TINYINT NOT NULL DEFAULT 0,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_noti_user (notification_id, to_user_id),
  KEY idx_user_read (to_user_id, read_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息收件箱';

-- -------------------------------------------------------------
-- 六、内容发布：新闻 / 广告 / 赛事 / 红黑榜
-- -------------------------------------------------------------

DROP TABLE IF EXISTS pub_news;
CREATE TABLE pub_news (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title        VARCHAR(200) NOT NULL,
  sub_title    VARCHAR(200) DEFAULT NULL,
  news_type    TINYINT NOT NULL DEFAULT 1 COMMENT '1通知公告 2校园活动 3学院动态 4其他',
  cover_image  VARCHAR(255) DEFAULT NULL,
  visible_org_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '可见组织根(1=全校公开)',
  start_time   DATETIME NOT NULL,
  end_time     DATETIME NOT NULL,
  status       TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1正式',
  view_count   INT NOT NULL DEFAULT 0,
  like_count   INT NOT NULL DEFAULT 0,
  publisher_id BIGINT UNSIGNED DEFAULT NULL,
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_status_time (status, start_time),
  KEY idx_org (visible_org_id),
  KEY idx_type (news_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新闻速递';

DROP TABLE IF EXISTS pub_news_content;
CREATE TABLE pub_news_content (
  id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  news_id BIGINT UNSIGNED NOT NULL,
  content MEDIUMTEXT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_news (news_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新闻正文';

DROP TABLE IF EXISTS pub_news_image;
CREATE TABLE pub_news_image (
  id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  news_id BIGINT UNSIGNED NOT NULL,
  image   VARCHAR(255) NOT NULL,
  sort    INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_news (news_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新闻配图';

DROP TABLE IF EXISTS pub_ad;
CREATE TABLE pub_ad (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title          VARCHAR(200) NOT NULL,
  sub_title      VARCHAR(200) DEFAULT NULL,
  image          VARCHAR(255) NOT NULL,
  link_type      TINYINT NOT NULL DEFAULT 1 COMMENT '1站内 2站外',
  link           VARCHAR(500) DEFAULT NULL,
  start_time     DATETIME NOT NULL,
  end_time       DATETIME NOT NULL,
  status         TINYINT NOT NULL DEFAULT 1 COMMENT '0下线 1上线',
  visible_org_id BIGINT UNSIGNED NOT NULL DEFAULT 1,
  sort           INT NOT NULL DEFAULT 0,
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_status_time (status, start_time, end_time),
  KEY idx_org (visible_org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广告 Banner';

DROP TABLE IF EXISTS pub_match;
CREATE TABLE pub_match (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title               VARCHAR(200) NOT NULL,
  sub_title           VARCHAR(200) DEFAULT NULL,
  cover_image         VARCHAR(255) DEFAULT NULL,
  visible_org_id      BIGINT UNSIGNED NOT NULL DEFAULT 1,
  start_show_time     DATETIME NOT NULL,
  start_register_time DATETIME NOT NULL,
  end_register_time   DATETIME NOT NULL,
  match_time          DATETIME NOT NULL,
  group_count         INT NOT NULL DEFAULT 1 COMMENT '组队人数上限',
  manager_id          BIGINT UNSIGNED DEFAULT NULL COMMENT '负责人 employee_id',
  status              TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2已结束',
  create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted             TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_status (status),
  KEY idx_org (visible_org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='赛事风云';

DROP TABLE IF EXISTS pub_match_content;
CREATE TABLE pub_match_content (
  id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  match_id BIGINT UNSIGNED NOT NULL,
  content  MEDIUMTEXT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_match (match_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='赛事详情正文';

DROP TABLE IF EXISTS pub_match_register;
CREATE TABLE pub_match_register (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  match_id      BIGINT UNSIGNED NOT NULL,
  leader_employee_id BIGINT UNSIGNED NOT NULL COMMENT '报名人(队长)',
  team_name     VARCHAR(64) DEFAULT NULL,
  member_names  VARCHAR(500) DEFAULT NULL COMMENT '队员姓名快照，逗号分隔',
  contact       VARCHAR(64) DEFAULT NULL,
  remark        VARCHAR(500) DEFAULT NULL,
  status        TINYINT NOT NULL DEFAULT 0 COMMENT '0待审 1已通过 2已拒绝 3已取消',
  audit_by      BIGINT UNSIGNED DEFAULT NULL,
  audit_time    DATETIME DEFAULT NULL,
  audit_remark  VARCHAR(255) DEFAULT NULL,
  create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_match_leader (match_id, leader_employee_id),
  KEY idx_match_status (match_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='赛事报名';

DROP TABLE IF EXISTS pub_rank_label;
CREATE TABLE pub_rank_label (
  id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name  VARCHAR(16) NOT NULL COMMENT '喜/优/奖/赞/警/退/缺/罚',
  red   TINYINT NOT NULL DEFAULT 1 COMMENT '1红榜 0黑榜',
  sort  INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_name_red (name, red)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='红黑榜标签';

DROP TABLE IF EXISTS pub_rank;
CREATE TABLE pub_rank (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  employee_id      BIGINT UNSIGNED NOT NULL,
  title            VARCHAR(200) NOT NULL,
  sub_title        VARCHAR(200) DEFAULT NULL,
  red              TINYINT NOT NULL DEFAULT 1 COMMENT '1红榜 0黑榜',
  label_id         BIGINT UNSIGNED DEFAULT NULL,
  type             TINYINT NOT NULL DEFAULT 1 COMMENT '1学业 2德育 3体育 4实践 5纪律',
  achievement      VARCHAR(500) DEFAULT NULL,
  update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '榜单时间',
  status           TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1发布',
  relative_visible TINYINT NOT NULL DEFAULT 1 COMMENT '家长是否可见',
  image            VARCHAR(255) DEFAULT NULL,
  publisher_id     BIGINT UNSIGNED DEFAULT NULL,
  like_count       INT NOT NULL DEFAULT 0,
  create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted          TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_emp (employee_id, status),
  KEY idx_red_time (red, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人红黑榜';

DROP TABLE IF EXISTS pub_rank_content;
CREATE TABLE pub_rank_content (
  id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  rank_id BIGINT UNSIGNED NOT NULL,
  content MEDIUMTEXT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rank (rank_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='红黑榜详情正文';

DROP TABLE IF EXISTS pub_like;
CREATE TABLE pub_like (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  target_type VARCHAR(16) NOT NULL COMMENT 'NEWS/RANK',
  target_id   BIGINT UNSIGNED NOT NULL,
  user_id     BIGINT UNSIGNED NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_target_user (target_type, target_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞';

DROP TABLE IF EXISTS pub_share_link;
CREATE TABLE pub_share_link (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  target_type VARCHAR(16) NOT NULL,
  target_id   BIGINT UNSIGNED NOT NULL,
  short_code  VARCHAR(16) NOT NULL,
  creator_id  BIGINT UNSIGNED DEFAULT NULL,
  expire_time DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (short_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享短链(海报二维码)';

-- -------------------------------------------------------------
-- 七、教务：课表 + 课程评价（补齐缺口）
-- -------------------------------------------------------------

DROP TABLE IF EXISTS edu_course;
CREATE TABLE edu_course (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  course_code  VARCHAR(32) NOT NULL COMMENT '课程号',
  name         VARCHAR(128) NOT NULL,
  teacher_name VARCHAR(64) DEFAULT NULL,
  credit       DECIMAL(3,1) DEFAULT NULL,
  class_org_id BIGINT UNSIGNED DEFAULT NULL COMMENT '授课班级组织',
  term         VARCHAR(16) NOT NULL COMMENT '2026-2027-1',
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted      TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code_term (course_code, term),
  KEY idx_class (class_org_id, term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程';

DROP TABLE IF EXISTS edu_timetable;
CREATE TABLE edu_timetable (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  course_id     BIGINT UNSIGNED NOT NULL,
  class_org_id  BIGINT UNSIGNED NOT NULL,
  term          VARCHAR(16) NOT NULL,
  week_day      TINYINT NOT NULL COMMENT '1-7',
  section_start TINYINT NOT NULL COMMENT '第1-12节',
  section_end   TINYINT NOT NULL,
  location      VARCHAR(64) DEFAULT NULL,
  weeks         VARCHAR(120) NOT NULL DEFAULT '1-18' COMMENT '周次范围，如 1-8,10-16',
  PRIMARY KEY (id),
  KEY idx_class_term (class_org_id, term, week_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课表条目';

DROP TABLE IF EXISTS edu_eval;
CREATE TABLE edu_eval (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  employee_id   BIGINT UNSIGNED NOT NULL,
  course_id     BIGINT UNSIGNED NOT NULL,
  term          VARCHAR(16) NOT NULL,
  score_teach   TINYINT NOT NULL COMMENT '1-5',
  score_content TINYINT NOT NULL,
  score_atmos   TINYINT NOT NULL,
  score_total   DECIMAL(3,1) NOT NULL,
  content       VARCHAR(1000) DEFAULT NULL,
  anonymous     TINYINT NOT NULL DEFAULT 1,
  status        TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0隐藏',
  create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_emp_course (employee_id, course_id, term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程评价';

-- -------------------------------------------------------------
-- 八、奖学金评定（补齐缺口）
-- -------------------------------------------------------------

DROP TABLE IF EXISTS sch_plan;
CREATE TABLE sch_plan (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name        VARCHAR(128) NOT NULL,
  org_id      BIGINT UNSIGNED NOT NULL COMMENT '适用组织(学院/系)',
  term        VARCHAR(16) NOT NULL,
  quota       INT NOT NULL DEFAULT 1 COMMENT '名额',
  amount      DECIMAL(10,2) DEFAULT NULL COMMENT '金额',
  apply_start DATETIME NOT NULL,
  apply_end   DATETIME NOT NULL,
  rule_json   JSON COMMENT '规则：GPA下限/红榜加分/黑榜扣分等',
  status      TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1报名中 2评审中 3公示中 4已结束',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted     TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_org_status (org_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖学金计划';

DROP TABLE IF EXISTS sch_apply;
CREATE TABLE sch_apply (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  plan_id      BIGINT UNSIGNED NOT NULL,
  employee_id  BIGINT UNSIGNED NOT NULL,
  class_org_id BIGINT UNSIGNED DEFAULT NULL,
  gpa          DECIMAL(3,2) DEFAULT NULL,
  extra_score  DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '红黑榜加减分',
  total_score  DECIMAL(5,2) NOT NULL DEFAULT 0,
  materials    TEXT COMMENT '证明材料 JSON 数组',
  statement    VARCHAR(1000) DEFAULT NULL COMMENT '个人陈述',
  status       SMALLINT NOT NULL DEFAULT 10
               COMMENT '10待审 20辅导员通过 30院系通过 40已通过 50已驳回 60已公示 70已发放',
  rank_no      INT DEFAULT NULL COMMENT '评审名次',
  current_step TINYINT NOT NULL DEFAULT 1,
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plan_emp (plan_id, employee_id),
  KEY idx_status (plan_id, status, total_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖学金申请';

DROP TABLE IF EXISTS sch_approve;
CREATE TABLE sch_approve (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  apply_id    BIGINT UNSIGNED NOT NULL,
  step        TINYINT NOT NULL COMMENT '1辅导员 2院系 3学生处',
  approver_id BIGINT UNSIGNED DEFAULT NULL,
  result      TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1通过 2驳回',
  reason      VARCHAR(255) DEFAULT NULL,
  handle_time DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_apply (apply_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖学金审批流';

DROP TABLE IF EXISTS sch_publicity;
CREATE TABLE sch_publicity (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  plan_id    BIGINT UNSIGNED NOT NULL,
  start_time DATETIME NOT NULL,
  end_time   DATETIME NOT NULL,
  result_json JSON COMMENT '公示名单快照',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖学金公示';

-- -------------------------------------------------------------
-- 九、可选模块：今日任务 / 我的风险（P2）
-- -------------------------------------------------------------

DROP TABLE IF EXISTS task_today;
CREATE TABLE task_today (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  employee_id  BIGINT UNSIGNED NOT NULL,
  task_date    DATE NOT NULL,
  title        VARCHAR(128) NOT NULL,
  content      VARCHAR(500) DEFAULT NULL,
  deadline     DATETIME DEFAULT NULL,
  status       TINYINT NOT NULL DEFAULT 0 COMMENT '0待完成 1已提交 2已完成 3逾期',
  submit_text  VARCHAR(1000) DEFAULT NULL,
  submit_time  DATETIME DEFAULT NULL,
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_emp_date (employee_id, task_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='今日任务';

DROP TABLE IF EXISTS risk_record;
CREATE TABLE risk_record (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  employee_id    BIGINT UNSIGNED NOT NULL,
  risk_level     TINYINT NOT NULL DEFAULT 1 COMMENT '1低 2中 3高',
  overdue_count  INT NOT NULL DEFAULT 0,
  interview_time DATETIME DEFAULT NULL COMMENT '面谈时间',
  warn_time      DATETIME DEFAULT NULL,
  status         TINYINT NOT NULL DEFAULT 0 COMMENT '0监控中 1已解除',
  remark         VARCHAR(255) DEFAULT NULL,
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_emp (employee_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险预警';

-- -------------------------------------------------------------
-- 十、公共支撑
-- -------------------------------------------------------------

DROP TABLE IF EXISTS sys_file;
CREATE TABLE sys_file (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  namespace   VARCHAR(32) NOT NULL COMMENT 'avatar/news/match/rank/ad/leave',
  file_name   VARCHAR(255) NOT NULL,
  url         VARCHAR(255) NOT NULL,
  size        BIGINT DEFAULT NULL,
  content_type VARCHAR(128) DEFAULT NULL,
  uploader_id BIGINT UNSIGNED DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ns (namespace)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传文件';

DROP TABLE IF EXISTS sys_dict;
CREATE TABLE sys_dict (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dict_type  VARCHAR(32) NOT NULL,
  dict_value VARCHAR(32) NOT NULL,
  dict_label VARCHAR(64) NOT NULL,
  sort       INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_value (dict_type, dict_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典';

DROP TABLE IF EXISTS sys_operation_log;
CREATE TABLE sys_operation_log (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED DEFAULT NULL,
  module      VARCHAR(32) NOT NULL,
  action      VARCHAR(64) NOT NULL,
  target_id   VARCHAR(64) DEFAULT NULL,
  params      TEXT,
  ip          VARCHAR(64) DEFAULT NULL,
  cost_ms     INT DEFAULT NULL,
  success     TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, create_time),
  KEY idx_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- 初始化数据
-- =============================================================

INSERT INTO sys_role (code, name, remark) VALUES
('STUDENT','学生',NULL),
('MONITOR','学委','班级学生干部，可查看并分享班级已通过请假'),
('COUNSELOR','辅导员','一级审批'),
('SECRETARY','书记','二级审批(>3天)'),
('TEACHER','授课老师',NULL),
('PARENT','家长','与学生关联后可见部分内容'),
('ADMIN','管理员','后台管理');

INSERT INTO org_employee_type (name) VALUES ('学生'),('学委'),('教师'),('辅导员'),('书记'),('行政');

INSERT INTO org_position (name, level) VALUES ('校长',1),('院长',2),('书记',2),('辅导员',3),('班主任',4),('学委',5),('班长',5);

INSERT INTO pub_rank_label (name, red, sort) VALUES
('喜',1,1),('优',1,2),('奖',1,3),('赞',1,4),
('警',0,1),('退',0,2),('缺',0,3),('罚',0,4);

INSERT INTO sys_dict (dict_type, dict_value, dict_label, sort) VALUES
('leave_status','10','待辅导员审批',10),
('leave_status','11','待书记审批',11),
('leave_status','20','已通过待销假',20),
('leave_status','21','已销假',21),
('leave_status','30','已驳回',30),
('leave_status','40','已取消',40),
('leave_status','50','超期未销假',50),
('msg_level','0','普通',0),('msg_level','1','紧急',1),('msg_level','2','警告',2),('msg_level','3','致命',3),
('news_type','1','通知公告',1),('news_type','2','校园活动',2),('news_type','3','学院动态',3),('news_type','4','其他',4),
('gender','0','男',0),('gender','1','女',1),('gender','2','保密',2),
('identity_type','1','身份证',1),('identity_type','2','护照',2),('identity_type','3','军人证',3),('identity_type','4','港澳台',4);

-- 示例组织树：1 示例学院 > 2 信息工程学院 > 3 软件工程系 > 4 软工1班
INSERT INTO org_unit (id, name, type, parent_id, path, sort) VALUES
(1,'示例学院',1,NULL,'/1/',0),
(2,'信息工程学院',2,1,'/1/2/',0),
(3,'软件工程系',3,2,'/1/2/3/',0),
(4,'软件工程2025级1班',4,3,'/1/2/3/4/',0);

INSERT INTO org_college (org_id, major_count, position, description) VALUES (2, 7, '福建福州', '信息工程学院');
INSERT INTO org_class (org_id, department_id, enrollment_date, graduation_date) VALUES (4, 3, '2025-09-01', '2029-06-30');
