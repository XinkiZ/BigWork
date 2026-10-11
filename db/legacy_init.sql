-- =====================================================================
-- 遗留课程目录数据库初始化脚本（模拟 DEC VAX + Ingres 老系统）
-- 用法：以 root 身份执行（MySQL Workbench / 命令行均可）
--   mysql -u root -p < db/legacy_init.sql
-- 说明：本库模拟学院的遗留课程目录系统，只读使用；为体现"遗留"特征，
--       采用大写下划线老式命名、无外键约束、无字符集精细化设置。
-- =====================================================================

DROP DATABASE IF EXISTS legacy_catalog;
CREATE DATABASE legacy_catalog CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 只读账号：物理上只授予 SELECT 权限，保证新系统无法修改遗留数据
CREATE USER IF NOT EXISTS 'legacy_ro'@'localhost' IDENTIFIED BY 'legacy_ro123';
GRANT SELECT ON legacy_catalog.* TO 'legacy_ro'@'localhost';
FLUSH PRIVILEGES;

USE legacy_catalog;

-- 课程表（老式名称 COURSE_TBL）
CREATE TABLE COURSE_TBL (
  COURSE_NO    VARCHAR(16)  NOT NULL,
  COURSE_TITLE VARCHAR(64)  NOT NULL,
  CREDIT_HRS   INT          NOT NULL,
  DEPT_CODE    VARCHAR(16)  NOT NULL,
  CATALOG_YR   VARCHAR(8)   NOT NULL,
  PRIMARY KEY (COURSE_NO)
) COMMENT='课程目录主表（遗留系统维护，新系统只读）';

-- 先修课程表
CREATE TABLE PREREQ_TBL (
  COURSE_NO  VARCHAR(16) NOT NULL,
  PREREQ_NO  VARCHAR(16) NOT NULL,
  PRIMARY KEY (COURSE_NO, PREREQ_NO)
) COMMENT='课程先修关系表';

-- 教授授课资格表
CREATE TABLE QUALIFY_TBL (
  PROF_NO    VARCHAR(16) NOT NULL,
  COURSE_NO  VARCHAR(16) NOT NULL,
  PRIMARY KEY (PROF_NO, COURSE_NO)
) COMMENT='教授授课资格表';

-- ------------------------- 课程目录种子数据 -------------------------
INSERT INTO COURSE_TBL (COURSE_NO, COURSE_TITLE, CREDIT_HRS, DEPT_CODE, CATALOG_YR) VALUES
('CS100', '计算机导论',       1, 'CS',    '2026'),
('CS101', '程序设计基础',     3, 'CS',    '2026'),
('CS102', '数据结构',         3, 'CS',    '2026'),
('CS201', '计算机组成原理',   3, 'CS',    '2026'),
('CS202', '操作系统',         3, 'CS',    '2026'),
('CS301', '软件工程',         2, 'CS',    '2026'),
('MA101', '高等数学 I',       4, 'MATH',  '2026'),
('MA201', '线性代数',         3, 'MATH',  '2026'),
('EN101', '大学英语 I',       2, 'FL',    '2026'),
('EN201', '大学英语 II',      2, 'FL',    '2026'),
('PH101', '大学物理',         3, 'PHYS',  '2026'),
('PE101', '体育 I',           1, 'PE',    '2026');

INSERT INTO PREREQ_TBL (COURSE_NO, PREREQ_NO) VALUES
('CS102', 'CS101'),
('CS202', 'CS102'),
('CS301', 'CS102'),
('MA201', 'MA101'),
('EN201', 'EN101');

INSERT INTO QUALIFY_TBL (PROF_NO, COURSE_NO) VALUES
('P001', 'CS101'), ('P001', 'CS102'), ('P001', 'CS201'), ('P001', 'CS202'), ('P001', 'CS301'),
('P002', 'MA101'), ('P002', 'MA201'),
('P003', 'CS100'), ('P003', 'CS101'), ('P003', 'CS102'), ('P003', 'CS202'), ('P003', 'CS301'),
('P004', 'EN101'), ('P004', 'EN201'),
('P005', 'PH101'), ('P005', 'PE101');

-- 完整性自检（可选执行）
-- SELECT c.COURSE_NO, c.COURSE_TITLE, c.CREDIT_HRS FROM COURSE_TBL c ORDER BY c.COURSE_NO;