-- =====================================================================
-- 主数据库初始化脚本（Wylie College 学生选课注册系统）
-- 用法：以 root 身份执行（MySQL Workbench / 命令行均可）
--   mysql -u root -p < db/init.sql
-- 说明：脚本可重复执行（会先删除并重建 bigwork / bigwork_test 两个库），
--       演示前重新执行本脚本即可恢复初始数据。
-- =====================================================================

DROP DATABASE IF EXISTS bigwork;
DROP DATABASE IF EXISTS bigwork_test;
CREATE DATABASE bigwork CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE bigwork_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 应用专用账号（口令可自行修改，修改后需同步修改 backend/src/main/resources/application.yml）
CREATE USER IF NOT EXISTS 'bigwork_app'@'localhost' IDENTIFIED BY 'bigwork123';
ALTER USER 'bigwork_app'@'localhost' IDENTIFIED BY 'bigwork123';
GRANT ALL PRIVILEGES ON bigwork.* TO 'bigwork_app'@'localhost';
GRANT ALL PRIVILEGES ON bigwork_test.* TO 'bigwork_app'@'localhost';
FLUSH PRIVILEGES;

USE bigwork;

-- ------------------------- 学期 -------------------------
CREATE TABLE semester (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  code                VARCHAR(32)  NOT NULL UNIQUE COMMENT '学期编码',
  name                VARCHAR(64)  NOT NULL COMMENT '学期名称',
  start_date          DATE         NOT NULL,
  end_date            DATE         NOT NULL,
  registration_status VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN=注册开放 CLOSED=注册已关闭',
  adjustment_start    DATE         NULL COMMENT '选课调整期开始',
  adjustment_end      DATE         NULL COMMENT '选课调整期结束',
  current_flag        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1=当前学期'
) COMMENT='学期';

-- ------------------------- 学生 -------------------------
CREATE TABLE student (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_no      VARCHAR(16) NOT NULL UNIQUE COMMENT '学号（唯一）',
  name            VARCHAR(32) NOT NULL,
  birth_date      DATE        NULL,
  ssn             VARCHAR(32) NULL COMMENT '社保号',
  status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/LEAVE/GRADUATED',
  graduation_date DATE        NULL,
  password_hash   VARCHAR(100) NOT NULL
) COMMENT='学生（新增时由系统生成唯一学号）';

-- ------------------------- 教授 -------------------------
CREATE TABLE professor (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  professor_no  VARCHAR(16) NOT NULL UNIQUE COMMENT '教授编号（唯一）',
  name          VARCHAR(32) NOT NULL,
  birth_date    DATE        NULL,
  ssn           VARCHAR(32) NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/LEAVE/RETIRED',
  department    VARCHAR(64) NULL,
  password_hash VARCHAR(100) NOT NULL
) COMMENT='教授（新增时由系统生成唯一编号）';

-- ------------------------- 注册管理员 -------------------------
CREATE TABLE registrar (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  username      VARCHAR(32)  NOT NULL UNIQUE,
  name          VARCHAR(32)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL
) COMMENT='注册管理员';

-- ------------------------- 课程班（开课） -------------------------
CREATE TABLE course_offering (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  semester_id BIGINT      NOT NULL,
  course_no   VARCHAR(16) NOT NULL COMMENT '课程号（来自遗留课程目录）',
  title       VARCHAR(64) NOT NULL COMMENT '课程名称（目录缓存值）',
  credits     INT         NOT NULL COMMENT '学分',
  department  VARCHAR(64) NULL,
  professor_id BIGINT     NULL COMMENT '授课教授，NULL=未分配',
  day_of_week TINYINT     NOT NULL COMMENT '1=周一 … 7=周日',
  start_time  TIME        NOT NULL,
  end_time    TIME        NOT NULL,
  room        VARCHAR(32) NULL,
  capacity    INT         NOT NULL DEFAULT 10 COMMENT '容量上限（题目规则：最多10人）',
  min_students INT        NOT NULL DEFAULT 3 COMMENT '最少开课人数（题目规则：不足3人取消）',
  status      VARCHAR(16) NOT NULL DEFAULT 'PROPOSED' COMMENT 'PROPOSED=待确认 CONFIRMED=已生效 CANCELLED=已取消',
  CONSTRAINT fk_offering_semester  FOREIGN KEY (semester_id)  REFERENCES semester(id),
  CONSTRAINT fk_offering_professor FOREIGN KEY (professor_id) REFERENCES professor(id) ON DELETE SET NULL,
  INDEX idx_offering_semester (semester_id)
) COMMENT='课程班（特定学期内课程的具体开班）';

-- ------------------------- 课表 -------------------------
CREATE TABLE schedule (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id  BIGINT      NOT NULL,
  semester_id BIGINT      NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT=暂存 SUBMITTED=已提交',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_schedule (student_id, semester_id),
  CONSTRAINT fk_schedule_student  FOREIGN KEY (student_id)  REFERENCES student(id)  ON DELETE CASCADE,
  CONSTRAINT fk_schedule_semester FOREIGN KEY (semester_id) REFERENCES semester(id)
) COMMENT='学生课表（每学期一张）';

-- ------------------------- 课表条目 -------------------------
CREATE TABLE schedule_item (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  schedule_id BIGINT      NOT NULL,
  offering_id BIGINT      NOT NULL,
  item_type   VARCHAR(8)  NOT NULL COMMENT 'PRIMARY=主选 BACKUP=备选',
  sort_order  INT         NOT NULL COMMENT '主选1-4；备选1-2，按顺序启用',
  status      VARCHAR(16) NOT NULL DEFAULT 'SELECTED' COMMENT 'SELECTED=已选择（暂存） REGISTERED=已注册 REMOVED=已移除',
  promoted    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '1=关闭注册时由备选补位启用',
  UNIQUE KEY uk_item (schedule_id, offering_id),
  CONSTRAINT fk_item_schedule FOREIGN KEY (schedule_id) REFERENCES schedule(id) ON DELETE CASCADE,
  CONSTRAINT fk_item_offering FOREIGN KEY (offering_id) REFERENCES course_offering(id)
) COMMENT='课表条目（主选/备选）';

-- ------------------------- 选课记录（名单） -------------------------
CREATE TABLE enrollment (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  offering_id BIGINT      NOT NULL,
  student_id  BIGINT      NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED=已注册 DROPPED=已退课',
  enrolled_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_enrollment (offering_id, student_id),
  CONSTRAINT fk_enr_offering FOREIGN KEY (offering_id) REFERENCES course_offering(id),
  CONSTRAINT fk_enr_student  FOREIGN KEY (student_id)  REFERENCES student(id) ON DELETE CASCADE
) COMMENT='选课记录（课程班名单，容量校验依据）';

-- ------------------------- 成绩 -------------------------
CREATE TABLE grade (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  offering_id  BIGINT      NOT NULL,
  student_id   BIGINT      NOT NULL,
  professor_id BIGINT      NULL COMMENT '录入教授',
  grade_value  VARCHAR(1)  NULL COMMENT 'A/B/C/D/F/I，NULL=未录入',
  updated_at   DATETIME    NULL,
  UNIQUE KEY uk_grade (offering_id, student_id),
  CONSTRAINT fk_grade_offering  FOREIGN KEY (offering_id)  REFERENCES course_offering(id),
  CONSTRAINT fk_grade_student   FOREIGN KEY (student_id)   REFERENCES student(id),
  CONSTRAINT fk_grade_professor FOREIGN KEY (professor_id) REFERENCES professor(id) ON DELETE SET NULL
) COMMENT='成绩（仅教授可录入，仅对应课程班的学生）';

-- ------------------------- 计费交易 -------------------------
CREATE TABLE billing_transaction (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id      BIGINT        NOT NULL,
  semester_id     BIGINT        NOT NULL,
  amount          DECIMAL(10,2) NOT NULL COMMENT '正数=应收，负数=退费（调整期差额）',
  tx_type         VARCHAR(16)   NOT NULL COMMENT 'INITIAL=关闭注册初始账单 ADJUSTMENT=调整期差额',
  status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
  retry_count     INT           NOT NULL DEFAULT 0,
  last_attempt_at DATETIME      NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  description     VARCHAR(255)  NULL,
  CONSTRAINT fk_bill_student  FOREIGN KEY (student_id)  REFERENCES student(id) ON DELETE CASCADE,
  CONSTRAINT fk_bill_semester FOREIGN KEY (semester_id) REFERENCES semester(id)
) COMMENT='计费交易（对应计费系统同步，失败自动重试）';

-- =====================================================================
-- 种子数据
-- =====================================================================

-- 学期：上一学期（历史，含成绩）+ 当前学期（选课开放中）+ 未来学期（演示切换当前学期/开班，注册未开放）
INSERT INTO semester (id, code, name, start_date, end_date, registration_status, adjustment_start, adjustment_end, current_flag) VALUES
(1, '2025-2026-2', '2025-2026学年第二学期', '2026-02-23', '2026-07-03', 'CLOSED', '2026-03-02', '2026-03-13', 0),
(2, '2026-2027-1', '2026-2027学年第一学期', '2026-09-07', '2027-01-15', 'OPEN',   '2026-10-01', '2027-01-15', 1),
(3, '2026-2027-2', '2026-2027学年第二学期', '2027-02-22', '2027-07-02', 'CLOSED', '2027-03-01', '2027-03-12', 0);

-- 演示账号口径：学生/教授初始口令 123456，注册管理员初始口令 admin123（BCrypt 哈希）
-- 学生 16 人（2026001-2026010 为主角学生，2026011-2026016 为背景学生，用于填充课程班人数）
INSERT INTO student (id, student_no, name, birth_date, ssn, status, graduation_date, password_hash) VALUES
(1,  '2026001', '陈晨',  '2005-03-12', '310101200503121234', 'ACTIVE', '2028-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(2,  '2026002', '刘洋',  '2005-07-08', '310101200507081234', 'ACTIVE', '2028-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(3,  '2026003', '张涵',  '2005-11-21', '310101200511211234', 'ACTIVE', '2028-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(4,  '2026004', '王梓',  '2005-01-30', '310101200501301234', 'ACTIVE', '2028-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(5,  '2026005', '李思远','2005-05-16', '310101200505161234', 'ACTIVE', '2028-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(6,  '2026006', '赵雪',  '2005-09-02', '310101200509021234', 'ACTIVE', '2028-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(7,  '2026007', '孙浩',  '2006-02-14', '310101200602141234', 'ACTIVE', '2029-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(8,  '2026008', '周琳',  '2006-06-27', '310101200606271234', 'ACTIVE', '2029-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(9,  '2026009', '吴迪',  '2006-10-05', '310101200610051234', 'ACTIVE', '2029-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(10, '2026010', '郑爽',  '2006-04-19', '310101200604191234', 'ACTIVE', '2029-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(11, '2026011', '钱峰',  '2005-12-03', '310101200512031234', 'ACTIVE', '2028-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(12, '2026012', '冯雨',  '2005-08-25', '310101200508251234', 'ACTIVE', '2028-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(13, '2026013', '蒋涛',  '2005-10-11', '310101200510111234', 'ACTIVE', '2028-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(14, '2026014', '沈悦',  '2006-03-08', '310101200603081234', 'ACTIVE', '2029-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(15, '2026015', '韩磊',  '2006-07-30', '310101200607301234', 'ACTIVE', '2029-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(16, '2026016', '曹静',  '2006-11-17', '310101200611171234', 'ACTIVE', '2029-06-30', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu');

-- 教授 5 人（授课资格见遗留库 QUALIFY_TBL）
INSERT INTO professor (id, professor_no, name, birth_date, ssn, status, department, password_hash) VALUES
(1, 'P001', '张伟', '1978-05-20', '310101197805201111', 'ACTIVE', '计算机学院', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(2, 'P002', '李娜', '1982-09-14', '310101198209141111', 'ACTIVE', '数学学院',   '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(3, 'P003', '王强', '1975-12-01', '310101197512011111', 'ACTIVE', '计算机学院', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(4, 'P004', '赵敏', '1980-03-27', '310101198003271111', 'ACTIVE', '外国语学院', '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu'),
(5, 'P005', '孙芳', '1985-08-19', '310101198508191111', 'ACTIVE', '物理学院',   '$2a$10$mVyBTT5H6ATNw7iqN/eHl./jF0nFxFkkVx1yyaMiLLV3YuY1C2HLu');

-- 注册管理员
INSERT INTO registrar (id, username, name, password_hash) VALUES
(1, 'registrar', '教务处管理员', '$2a$10$ddmvVmGNSFAo.QHOx4fM9ubJlb6I9I3vj.g3/hAqs/bYB6lkeP2Mu');

-- =====================================================================
-- 课程班：上一学期（历史数据，用于成绩录入/成绩单演示）
-- =====================================================================
INSERT INTO course_offering (id, semester_id, course_no, title, credits, department, professor_id, day_of_week, start_time, end_time, room, capacity, min_students, status) VALUES
(12, 1, 'CS101', '程序设计基础',   3, 'CS',   1, 1, '08:00:00', '09:40:00', 'A102', 10, 3, 'CONFIRMED'),
(13, 1, 'CS201', '计算机组成原理', 3, 'CS',   1, 2, '08:00:00', '09:40:00', 'A202', 10, 3, 'CONFIRMED'),
(14, 1, 'EN101', '大学英语 I',     2, 'FL',   4, 3, '10:00:00', '11:40:00', 'C101', 10, 3, 'CONFIRMED'),
(15, 1, 'MA101', '高等数学 I',     4, 'MATH', 2, 1, '10:00:00', '11:40:00', 'B101', 10, 3, 'CONFIRMED');

-- 历史选课记录（上一学期）
INSERT INTO enrollment (offering_id, student_id, status) VALUES
(12, 1, 'REGISTERED'), (12, 2, 'REGISTERED'), (12, 3, 'REGISTERED'), (12, 4, 'REGISTERED'), (12, 5, 'REGISTERED'), (12, 8, 'REGISTERED'),
(13, 1, 'REGISTERED'), (13, 2, 'REGISTERED'), (13, 3, 'REGISTERED'), (13, 4, 'REGISTERED'),
(14, 1, 'REGISTERED'), (14, 2, 'REGISTERED'), (14, 3, 'REGISTERED'), (14, 4, 'REGISTERED'), (14, 6, 'REGISTERED'),
(15, 10, 'REGISTERED');

-- 历史成绩（部分留空用于演示"留空补录"；李思远 CS101 为 F，用于演示先修不通过）
INSERT INTO grade (offering_id, student_id, professor_id, grade_value, updated_at) VALUES
(12, 1, 1, 'A',    '2026-07-10 10:00:00'),
(12, 2, 1, 'B',    '2026-07-10 10:00:00'),
(12, 3, 1, 'C',    '2026-07-10 10:00:00'),
(12, 4, 1, 'D',    '2026-07-10 10:00:00'),
(12, 5, 1, 'F',    '2026-07-10 10:00:00'),
(12, 8, 1, NULL,   NULL),
(13, 1, 1, 'B',    '2026-07-10 10:05:00'),
(13, 2, 1, 'A',    '2026-07-10 10:05:00'),
(13, 3, 1, NULL,   NULL),
(13, 4, 1, 'C',    '2026-07-10 10:05:00'),
(14, 1, 4, 'A',    '2026-07-10 10:10:00'),
(14, 2, 4, 'A',    '2026-07-10 10:10:00'),
(14, 3, 4, 'B',    '2026-07-10 10:10:00'),
(14, 4, 4, NULL,   NULL),
(14, 6, 4, 'B',    '2026-07-10 10:10:00'),
(15, 10, 2, 'B',   '2026-07-10 10:15:00');
-- =====================================================================
-- 课程班：当前学期（注册开放中，演示主战场）
-- 演示要点：4号=0人取消；6号=仅1人取消并触发备选补位；
--          9号=满员（10/10）；10号=未分配教授（关闭注册时取消）；
--          1号/11号=同一时段（周一 08:00）用于时间冲突演示
-- =====================================================================
INSERT INTO course_offering (id, semester_id, course_no, title, credits, department, professor_id, day_of_week, start_time, end_time, room, capacity, min_students, status) VALUES
( 1, 2, 'CS101', '程序设计基础',   3, 'CS',   1,    1, '08:00:00', '09:40:00', 'A102', 10, 3, 'PROPOSED'),
( 2, 2, 'CS102', '数据结构',       3, 'CS',   3,    2, '10:00:00', '11:40:00', 'A201', 10, 3, 'PROPOSED'),
( 3, 2, 'CS201', '计算机组成原理', 3, 'CS',   1,    2, '08:00:00', '09:40:00', 'A202', 10, 3, 'PROPOSED'),
( 4, 2, 'CS202', '操作系统',       3, 'CS',   3,    4, '08:00:00', '09:40:00', 'A203', 10, 3, 'PROPOSED'),
( 5, 2, 'MA101', '高等数学 I',     4, 'MATH', 2,    1, '10:00:00', '11:40:00', 'B101', 10, 3, 'PROPOSED'),
( 6, 2, 'MA201', '线性代数',       3, 'MATH', 2,    3, '08:00:00', '09:40:00', 'B102', 10, 3, 'PROPOSED'),
( 7, 2, 'EN101', '大学英语 I',     2, 'FL',   4,    3, '10:00:00', '11:40:00', 'C101', 10, 3, 'PROPOSED'),
( 8, 2, 'EN201', '大学英语 II',    2, 'FL',   4,    4, '10:00:00', '11:40:00', 'C102', 10, 3, 'PROPOSED'),
( 9, 2, 'PH101', '大学物理',       3, 'PHYS', 5,    5, '08:00:00', '09:40:00', 'D101', 10, 3, 'PROPOSED'),
(10, 2, 'PE101', '体育 I',         1, 'PE',   NULL, 5, '10:00:00', '11:40:00', '体育馆', 10, 3, 'PROPOSED'),
(11, 2, 'CS100', '计算机导论',     1, 'CS',   3,    1, '08:00:00', '09:40:00', 'A101', 10, 3, 'PROPOSED');

-- =====================================================================
-- 学生课表（当前学期，均已提交）
-- 主选(PRIMARY)=REGISTERED；备选(BACKUP)=SELECTED（待关闭注册时按序启用）
-- =====================================================================
INSERT INTO schedule (id, student_id, semester_id, status) VALUES
(1,1,2,'SUBMITTED'),(2,2,2,'SUBMITTED'),(3,3,2,'SUBMITTED'),(4,4,2,'SUBMITTED'),
(5,5,2,'SUBMITTED'),(6,6,2,'SUBMITTED'),(7,7,2,'SUBMITTED'),(8,8,2,'SUBMITTED'),
(9,9,2,'SUBMITTED'),(10,10,2,'SUBMITTED'),(11,11,2,'SUBMITTED'),(12,12,2,'SUBMITTED'),
(13,13,2,'SUBMITTED'),(14,14,2,'SUBMITTED'),(15,15,2,'SUBMITTED'),(16,16,2,'SUBMITTED');

INSERT INTO schedule_item (schedule_id, offering_id, item_type, sort_order, status) VALUES
-- 陈晨：主选 数据结构/大学英语II/高等数学I/大学物理；备选 体育I、计算机导论
(1,  2,'PRIMARY',1,'REGISTERED'),(1,  8,'PRIMARY',2,'REGISTERED'),(1,  5,'PRIMARY',3,'REGISTERED'),(1,  9,'PRIMARY',4,'REGISTERED'),
(1, 10,'BACKUP',1,'SELECTED'),  (1, 11,'BACKUP',2,'SELECTED'),
-- 刘洋：同陈晨
(2,  2,'PRIMARY',1,'REGISTERED'),(2,  8,'PRIMARY',2,'REGISTERED'),(2,  5,'PRIMARY',3,'REGISTERED'),(2,  9,'PRIMARY',4,'REGISTERED'),
(2, 10,'BACKUP',1,'SELECTED'),  (2, 11,'BACKUP',2,'SELECTED'),
-- 张涵：同陈晨
(3,  2,'PRIMARY',1,'REGISTERED'),(3,  8,'PRIMARY',2,'REGISTERED'),(3,  5,'PRIMARY',3,'REGISTERED'),(3,  9,'PRIMARY',4,'REGISTERED'),
(3, 10,'BACKUP',1,'SELECTED'),  (3, 11,'BACKUP',2,'SELECTED'),
-- 王梓：主选 数据结构/计算机组成原理/大学英语I/大学物理；备选 计算机导论、体育I
(4,  2,'PRIMARY',1,'REGISTERED'),(4,  3,'PRIMARY',2,'REGISTERED'),(4,  7,'PRIMARY',3,'REGISTERED'),(4,  9,'PRIMARY',4,'REGISTERED'),
(4, 11,'BACKUP',1,'SELECTED'),  (4, 10,'BACKUP',2,'SELECTED'),
-- 李思远：主选 程序设计基础(重修)/计算机组成原理/高等数学I/大学物理；备选 体育I
(5,  1,'PRIMARY',1,'REGISTERED'),(5,  3,'PRIMARY',2,'REGISTERED'),(5,  5,'PRIMARY',3,'REGISTERED'),(5,  9,'PRIMARY',4,'REGISTERED'),
(5, 10,'BACKUP',1,'SELECTED'),
-- 赵雪：主选 大学英语II/计算机组成原理/体育I/计算机导论；备选 大学物理（已满，关闭注册时补位失败）
(6,  8,'PRIMARY',1,'REGISTERED'),(6,  3,'PRIMARY',2,'REGISTERED'),(6, 10,'PRIMARY',3,'REGISTERED'),(6, 11,'PRIMARY',4,'REGISTERED'),
(6,  9,'BACKUP',1,'SELECTED'),
-- 孙浩：主选 程序设计基础/计算机组成原理/高等数学I/体育I；备选 大学物理（补位失败）
(7,  1,'PRIMARY',1,'REGISTERED'),(7,  3,'PRIMARY',2,'REGISTERED'),(7,  5,'PRIMARY',3,'REGISTERED'),(7, 10,'PRIMARY',4,'REGISTERED'),
(7,  9,'BACKUP',1,'SELECTED'),
-- 周琳：主选 程序设计基础/高等数学I/大学英语I/大学物理；备选 体育I
(8,  1,'PRIMARY',1,'REGISTERED'),(8,  5,'PRIMARY',2,'REGISTERED'),(8,  7,'PRIMARY',3,'REGISTERED'),(8,  9,'PRIMARY',4,'REGISTERED'),
(8, 10,'BACKUP',1,'SELECTED'),
-- 吴迪：主选 程序设计基础/大学英语I/高等数学I/大学物理；备选 体育I
(9,  1,'PRIMARY',1,'REGISTERED'),(9,  7,'PRIMARY',2,'REGISTERED'),(9,  5,'PRIMARY',3,'REGISTERED'),(9,  9,'PRIMARY',4,'REGISTERED'),
(9, 10,'BACKUP',1,'SELECTED'),
-- 郑爽：主选 线性代数(仅1人，将被取消)/程序设计基础/大学英语I/大学物理；备选 高等数学I(补位成功)、体育I
(10, 6,'PRIMARY',1,'REGISTERED'),(10, 1,'PRIMARY',2,'REGISTERED'),(10, 7,'PRIMARY',3,'REGISTERED'),(10, 9,'PRIMARY',4,'REGISTERED'),
(10, 5,'BACKUP',1,'SELECTED'),  (10,10,'BACKUP',2,'SELECTED'),
-- 钱峰：主选 程序设计基础/计算机组成原理/大学物理/大学英语I；备选 高等数学I
(11, 1,'PRIMARY',1,'REGISTERED'),(11, 3,'PRIMARY',2,'REGISTERED'),(11, 9,'PRIMARY',3,'REGISTERED'),(11, 7,'PRIMARY',4,'REGISTERED'),
(11, 5,'BACKUP',1,'SELECTED'),
-- 冯雨：主选 计算机导论/计算机组成原理/大学英语I；备选 高等数学I
(12,11,'PRIMARY',1,'REGISTERED'),(12, 3,'PRIMARY',2,'REGISTERED'),(12, 7,'PRIMARY',3,'REGISTERED'),
(12, 5,'BACKUP',1,'SELECTED'),
-- 蒋涛：主选 程序设计基础/计算机组成原理/大学英语I；备选 高等数学I
(13, 1,'PRIMARY',1,'REGISTERED'),(13, 3,'PRIMARY',2,'REGISTERED'),(13, 7,'PRIMARY',3,'REGISTERED'),
(13, 5,'BACKUP',1,'SELECTED'),
-- 沈悦：主选 计算机导论/大学物理/高等数学I；备选 大学英语I
(14,11,'PRIMARY',1,'REGISTERED'),(14, 9,'PRIMARY',2,'REGISTERED'),(14, 5,'PRIMARY',3,'REGISTERED'),
(14, 7,'BACKUP',1,'SELECTED'),
-- 韩磊：主选 计算机导论/计算机组成原理/大学英语I；备选 高等数学I
(15,11,'PRIMARY',1,'REGISTERED'),(15, 3,'PRIMARY',2,'REGISTERED'),(15, 7,'PRIMARY',3,'REGISTERED'),
(15, 5,'BACKUP',1,'SELECTED'),
-- 曹静：主选 程序设计基础/大学英语I/计算机组成原理；备选 高等数学I
(16, 1,'PRIMARY',1,'REGISTERED'),(16, 7,'PRIMARY',2,'REGISTERED'),(16, 3,'PRIMARY',3,'REGISTERED'),
(16, 5,'BACKUP',1,'SELECTED');

-- 选课记录由课表主选条目自动推导，保证名单与课表一致
INSERT INTO enrollment (offering_id, student_id, status, enrolled_at)
SELECT si.offering_id, s.student_id, 'REGISTERED', NOW()
FROM schedule_item si
JOIN schedule s ON si.schedule_id = s.id
WHERE s.semester_id = 2
  AND si.item_type = 'PRIMARY'
  AND si.status = 'REGISTERED';

-- ------------------------- 数据自检（可参考） -------------------------
-- 各课程班当前注册人数：
-- SELECT o.id, o.course_no, o.title, COUNT(e.id) AS cnt
-- FROM course_offering o LEFT JOIN enrollment e ON e.offering_id = o.id AND e.status = 'REGISTERED'
-- WHERE o.semester_id = 2 GROUP BY o.id ORDER BY o.id;
-- =====================================================================
-- 测试库 bigwork_test：结构与数据与主库完全一致
-- （供 backend/src/test 下的 JUnit 集成测试使用；测试数据在事务中回滚）
-- =====================================================================
USE bigwork_test;

CREATE TABLE semester            LIKE bigwork.semester;
CREATE TABLE student             LIKE bigwork.student;
CREATE TABLE professor           LIKE bigwork.professor;
CREATE TABLE registrar           LIKE bigwork.registrar;
CREATE TABLE course_offering     LIKE bigwork.course_offering;
CREATE TABLE schedule            LIKE bigwork.schedule;
CREATE TABLE schedule_item       LIKE bigwork.schedule_item;
CREATE TABLE enrollment          LIKE bigwork.enrollment;
CREATE TABLE grade               LIKE bigwork.grade;
CREATE TABLE billing_transaction LIKE bigwork.billing_transaction;

INSERT INTO semester            SELECT * FROM bigwork.semester;
INSERT INTO student             SELECT * FROM bigwork.student;
INSERT INTO professor           SELECT * FROM bigwork.professor;
INSERT INTO registrar           SELECT * FROM bigwork.registrar;
INSERT INTO course_offering     SELECT * FROM bigwork.course_offering;
INSERT INTO schedule            SELECT * FROM bigwork.schedule;
INSERT INTO schedule_item       SELECT * FROM bigwork.schedule_item;
INSERT INTO enrollment          SELECT * FROM bigwork.enrollment;
INSERT INTO grade               SELECT * FROM bigwork.grade;
INSERT INTO billing_transaction SELECT * FROM bigwork.billing_transaction;

SELECT '主库与测试库初始化完成' AS message,
       (SELECT COUNT(*) FROM bigwork.student) AS main_students,
       (SELECT COUNT(*) FROM bigwork_test.student) AS test_students;