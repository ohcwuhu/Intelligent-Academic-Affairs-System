-- =====================================================================
-- 智能教务系统 - 数据库结构（第一阶段：教务业务）
-- 数据库：MySQL 8.0+，字符集 utf8mb4
--
-- 设计说明：
--   1. 不建物理外键，便于毕设环境随时清库重建。
--   2. 成绩与选课合并存于 enrollment：一名学生在一个教学班只有一条记录。
--   3. 学生的"已获学分"不存快照，由代码从成绩实时汇总，避免两处口径。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS iaas DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE iaas;

DROP TABLE IF EXISTS enrollment;
DROP TABLE IF EXISTS student_application;
DROP TABLE IF EXISTS exam;
DROP TABLE IF EXISTS program_course;
DROP TABLE IF EXISTS program_module;
DROP TABLE IF EXISTS program;
DROP TABLE IF EXISTS teaching_class;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS student;
DROP TABLE IF EXISTS teacher;
DROP TABLE IF EXISTS clazz;
DROP TABLE IF EXISTS major;
DROP TABLE IF EXISTS college;
DROP TABLE IF EXISTS term;
DROP TABLE IF EXISTS sys_user;

-- ---------------------------------------------------------------------
-- 1. 用户与权限
-- ---------------------------------------------------------------------
CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(50)  NOT NULL COMMENT '登录名（学号/工号/管理员账号）',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 散列后的口令',
    real_name   VARCHAR(50)  NOT NULL,
    role        VARCHAR(20)  NOT NULL COMMENT 'ADMIN/ACADEMIC/TEACHER/STUDENT',
    ref_id      BIGINT       NULL     COMMENT '关联业务主键：学生 ID 或教师 ID',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    last_login  DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB COMMENT='系统用户';

-- ---------------------------------------------------------------------
-- 2. 组织与基础数据
-- ---------------------------------------------------------------------
CREATE TABLE college (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_college_code (code)
) ENGINE=InnoDB COMMENT='学院';

CREATE TABLE major (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    code       VARCHAR(20) NOT NULL,
    name       VARCHAR(50) NOT NULL,
    college_id BIGINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_major_code (code),
    KEY idx_major_college (college_id)
) ENGINE=InnoDB COMMENT='专业';

CREATE TABLE clazz (
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    code     VARCHAR(20) NOT NULL,
    name     VARCHAR(50) NOT NULL,
    major_id BIGINT      NOT NULL,
    grade    INT         NOT NULL COMMENT '年级（入学年份）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_clazz_code (code),
    KEY idx_clazz_major (major_id)
) ENGINE=InnoDB COMMENT='班级';

CREATE TABLE term (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    code       VARCHAR(20) NOT NULL COMMENT '学期代码，如 2026-2027-1',
    name       VARCHAR(50) NOT NULL,
    start_date DATE        NOT NULL,
    end_date   DATE        NOT NULL,
    is_current TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_term_code (code)
) ENGINE=InnoDB COMMENT='学期';

-- ---------------------------------------------------------------------
-- 3. 学生与教师
-- ---------------------------------------------------------------------
CREATE TABLE student (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    student_no VARCHAR(20) NOT NULL,
    name       VARCHAR(50) NOT NULL,
    gender     VARCHAR(2)  NOT NULL DEFAULT '男',
    birth_date DATE        NULL,
    phone      VARCHAR(20) NULL,
    email      VARCHAR(80) NULL,
    college_id BIGINT      NOT NULL,
    major_id   BIGINT      NOT NULL,
    clazz_id   BIGINT      NOT NULL,
    grade      INT         NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT '在读' COMMENT '在读/休学/退学/毕业',
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_no (student_no),
    KEY idx_student_major (major_id),
    KEY idx_student_clazz (clazz_id)
) ENGINE=InnoDB COMMENT='学生';

CREATE TABLE teacher (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    teacher_no VARCHAR(20) NOT NULL,
    name       VARCHAR(50) NOT NULL,
    gender     VARCHAR(2)  NOT NULL DEFAULT '男',
    title      VARCHAR(20) NULL,
    college_id BIGINT      NOT NULL,
    phone      VARCHAR(20) NULL,
    email      VARCHAR(80) NULL,
    status     VARCHAR(20) NOT NULL DEFAULT '在职',
    PRIMARY KEY (id),
    UNIQUE KEY uk_teacher_no (teacher_no),
    KEY idx_teacher_college (college_id)
) ENGINE=InnoDB COMMENT='教师';

-- ---------------------------------------------------------------------
-- 4. 课程与教学班
-- ---------------------------------------------------------------------
CREATE TABLE course (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    credit      DECIMAL(3,1) NOT NULL,
    hours       INT          NOT NULL,
    course_type VARCHAR(20)  NOT NULL COMMENT '必修/选修/公选/实践',
    college_id  BIGINT       NOT NULL,
    assess_type VARCHAR(20)  NOT NULL DEFAULT '考试',
    status      TINYINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_code (code),
    KEY idx_course_college (college_id)
) ENGINE=InnoDB COMMENT='课程库';

-- 时间模型：一周内的连续节次区间 + 单双周标记。
-- 足以支撑选课冲突检测，又不必引入复杂的排课时刻表结构。
CREATE TABLE teaching_class (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    code          VARCHAR(30) NOT NULL,
    course_id     BIGINT      NOT NULL,
    teacher_id    BIGINT      NOT NULL,
    term_id       BIGINT      NOT NULL,
    capacity      INT         NOT NULL DEFAULT 60,
    enrolled      INT         NOT NULL DEFAULT 0,
    major_id      BIGINT      NULL COMMENT '面向专业，空表示面向全校',
    grade         INT         NULL COMMENT '面向年级，空表示不限年级',
    weekday       TINYINT     NOT NULL COMMENT '星期 1-7',
    start_section TINYINT     NOT NULL COMMENT '开始节次 1-12',
    end_section   TINYINT     NOT NULL COMMENT '结束节次 1-12',
    start_week    TINYINT     NOT NULL DEFAULT 1,
    end_week      TINYINT     NOT NULL DEFAULT 16,
    week_type     VARCHAR(10) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/ODD/EVEN',
    classroom     VARCHAR(50) NULL,
    status        VARCHAR(20) NOT NULL DEFAULT '开放' COMMENT '开放/停开/结课',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tc_code (code),
    KEY idx_tc_term (term_id),
    KEY idx_tc_teacher (teacher_id),
    KEY idx_tc_course (course_id),
    KEY idx_tc_major (major_id, grade)
) ENGINE=InnoDB COMMENT='教学班';

-- ---------------------------------------------------------------------
-- 5. 选课与成绩
-- ---------------------------------------------------------------------
CREATE TABLE enrollment (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    student_id        BIGINT       NOT NULL,
    teaching_class_id BIGINT       NOT NULL,
    term_id           BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'SELECTED' COMMENT 'SELECTED/DROPPED',
    score             DECIMAL(5,1) NULL COMMENT '总评成绩，NULL 表示未录入',
    score_status      VARCHAR(20)  NOT NULL DEFAULT '未录入',
    grade_point       DECIMAL(3,2) NULL,
    selected_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_tc (student_id, teaching_class_id),
    KEY idx_enroll_term (term_id),
    KEY idx_enroll_tc (teaching_class_id)
) ENGINE=InnoDB COMMENT='选课记录（含成绩）';

-- ---------------------------------------------------------------------
-- 6. 学生申请单（办事与审批）
--
-- 免听间听、重修、转专业、证明打印在教务处是同一件事：
-- 学生按规则提交、教务按规则审批、过程留痕，所以用一张表按 type 区分，
-- 而不是每种事项各建一张只有几列的表。
-- ---------------------------------------------------------------------
CREATE TABLE student_application (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    type          VARCHAR(30)  NOT NULL COMMENT 'ON_EXEMPT/RETAKE/TRANSFER_MAJOR/CERTIFICATE',
    student_id    BIGINT       NOT NULL,
    term_id       BIGINT       NULL,
    target        VARCHAR(200) NULL COMMENT '事项指向的对象名称',
    target_id     BIGINT       NULL COMMENT '对象 ID：重修是课程，转专业是专业，免听间听是教学班',
    reason        VARCHAR(500) NOT NULL,
    materials     VARCHAR(500) NULL COMMENT '学生自述携带的材料',
    status        VARCHAR(20)  NOT NULL DEFAULT '待审' COMMENT '待审/已通过/已驳回/已撤回',
    reviewer      VARCHAR(50)  NULL,
    review_note   VARCHAR(500) NULL,
    reviewed_at   DATETIME     NULL,
    precheck_note VARCHAR(500) NULL COMMENT '系统提交时自动判定的结论，供审批人参考',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_app_student (student_id, created_at),
    KEY idx_app_status (status, created_at)
) ENGINE=InnoDB COMMENT='学生申请单与审批';

-- ---------------------------------------------------------------------
-- 7. 考试安排
--
-- 一条记录 = 一个教学班的一场考试。学生看到的"我的考试"是它按本人选课过滤后的结果，
-- 所以不另建学生级的考试表，避免两处口径。
-- 时间段用 DATE + TIME 存，不用 DATETIME：考试是按"某天几点到几点"组织的，
-- 存成时间点会在跨天、时长比较上引入没必要的复杂度。
-- ---------------------------------------------------------------------
CREATE TABLE exam (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    teaching_class_id BIGINT      NOT NULL,
    term_id           BIGINT      NOT NULL,
    exam_type         VARCHAR(20) NOT NULL DEFAULT '期末考试' COMMENT '期末考试/补考/重修考试',
    exam_date         DATE        NOT NULL,
    start_time        TIME        NOT NULL,
    end_time          TIME        NOT NULL,
    classroom         VARCHAR(50) NULL,
    seat_no           VARCHAR(30) NULL COMMENT '座位号，可留空',
    note              VARCHAR(200) NULL COMMENT '考试形式（闭卷/开卷/上机）等补充说明',
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_exam_term (term_id, exam_date),
    KEY idx_exam_tc (teaching_class_id)
) ENGINE=InnoDB COMMENT='考试安排';

-- ---------------------------------------------------------------------
-- 8. 培养方案（要求学分与毕业审核的基准）
--
-- 三张表的关系：一个方案（program）有若干模块要求学分（program_module），
-- 模块下是计划课程（program_course）。
-- 方案不是手写的，是教务把培养方案 Excel 导进来的，所以这里保留来源说明，
-- 并且允许同一专业存在多份（按年级区分），审核时取"现行"的那一份。
-- ---------------------------------------------------------------------
CREATE TABLE program (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    major_id    BIGINT       NULL COMMENT '所属专业，按专业名称匹配；匹配不上留空',
    major_name  VARCHAR(80)  NOT NULL COMMENT '方案里写的专业名称，匹配不上时的兜底',
    grade       INT          NULL COMMENT '适用年级，空表示现行方案',
    title       VARCHAR(160) NOT NULL,
    degree      VARCHAR(60)  NULL COMMENT '授予学位',
    duration    VARCHAR(20)  NULL COMMENT '标准学制',
    min_credit  DECIMAL(5,1) NULL COMMENT '毕业最低学分',
    source_note VARCHAR(300) NULL COMMENT '来源说明（官网栏目、更新时间）',
    status      VARCHAR(20)  NOT NULL DEFAULT '现行' COMMENT '现行/停用',
    imported_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_program_major (major_id, status)
) ENGINE=InnoDB COMMENT='培养方案';

-- 学分结构：模块维度要求多少学分。毕业审核按它算缺口。
CREATE TABLE program_module (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    program_id BIGINT       NOT NULL,
    category   VARCHAR(80)  NOT NULL COMMENT '课程类别，如通识教育必修课',
    hours_text VARCHAR(40)  NULL COMMENT '学时/周数，原样保留（只有展示价值）',
    credit     DECIMAL(5,1) NOT NULL COMMENT '该类别要求学分',
    ratio      DECIMAL(6,4) NULL COMMENT '占比，原样保留',
    sort_no    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_pm_program (program_id, sort_no)
) ENGINE=InnoDB COMMENT='培养方案学分结构';

-- 计划课程：一门课在哪个学期开、多少学分、属于哪个模块。
CREATE TABLE program_course (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    program_id     BIGINT       NOT NULL,
    module         VARCHAR(80)  NOT NULL COMMENT '所属模块（培养方案里的工作表名）',
    group_name     VARCHAR(120) NULL COMMENT '方向/分组标题，如"网络技术与安全方向"',
    course_name    VARCHAR(120) NOT NULL,
    course_type    VARCHAR(20)  NOT NULL DEFAULT '理论' COMMENT '理论/实践',
    assess_type    VARCHAR(20)  NULL,
    credit         DECIMAL(5,1) NOT NULL,
    total_hours    INT          NULL,
    lab_hours      INT          NULL,
    computer_hours INT          NULL,
    term_no        INT          NULL COMMENT '第一个开课学期 1-8',
    week_hours     INT          NULL COMMENT '该学期周学时',
    note           VARCHAR(200) NULL,
    required       VARCHAR(20)  NULL COMMENT '必修/选修/任选',
    course_id      BIGINT       NULL COMMENT '能对上课程库时记下课程 ID',
    PRIMARY KEY (id),
    KEY idx_pc_program (program_id, module),
    KEY idx_pc_name (course_name)
) ENGINE=InnoDB COMMENT='培养方案课程';
