-- =====================================================================
-- 智能教务系统 - 治理层：审计、反馈、会话、知识缺口
--
-- 支撑四条 P0：
--   REQ-BASE-02 审计日志、REQ-QA-12 / REQ-BASE-03 反馈闭环、
--   REQ-QA-01 多轮会话、REQ-KB-05 知识缺口管理。
-- =====================================================================
USE iaas;

DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS feedback;
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_conversation;
DROP TABLE IF EXISTS knowledge_gap;

-- 审计日志：追加写，不提供修改与删除接口
CREATE TABLE audit_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    event_type     VARCHAR(30)  NOT NULL COMMENT 'ASK/SEARCH/INGEST/INJECTION/ACCESS',
    user_id        BIGINT       NULL,
    username       VARCHAR(50)  NULL,
    role           VARCHAR(20)  NULL,
    question       VARCHAR(500) NULL,
    intent         VARCHAR(20)  NULL,
    mode           VARCHAR(20)  NULL,
    hit_count      INT          NULL,
    citation_count INT          NULL,
    blocked        TINYINT      NOT NULL DEFAULT 0,
    reason         VARCHAR(200) NULL,
    duration_ms    INT          NULL,
    trace_id       VARCHAR(40)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_created (created_at),
    KEY idx_audit_user (user_id),
    KEY idx_audit_type (event_type)
) ENGINE=InnoDB COMMENT='审计日志';

CREATE TABLE feedback (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    username      VARCHAR(50)  NULL,
    role          VARCHAR(20)  NULL,
    question      VARCHAR(500) NOT NULL,
    answer_mode   VARCHAR(20)  NULL,
    answer_digest VARCHAR(500) NULL,
    citation_path VARCHAR(300) NULL,
    type          VARCHAR(20)  NOT NULL COMMENT 'USEFUL/USELESS/WRONG',
    detail        VARCHAR(1000) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT '待处理' COMMENT '待处理/处理中/已修正/无需处理',
    handler       VARCHAR(50)  NULL,
    handle_note   VARCHAR(500) NULL,
    handled_at    DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_fb_status (status),
    KEY idx_fb_created (created_at)
) ENGINE=InnoDB COMMENT='用户反馈与处理闭环';

CREATE TABLE chat_conversation (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(120) NULL,
    turn_count INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conv_user (user_id, updated_at)
) ENGINE=InnoDB COMMENT='问答会话';

CREATE TABLE chat_message (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT        NOT NULL,
    role            VARCHAR(20)   NOT NULL COMMENT 'user / assistant',
    content         TEXT          NOT NULL,
    intent          VARCHAR(20)   NULL,
    mode            VARCHAR(20)   NULL,
    citation_paths  VARCHAR(1000) NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_msg_conv (conversation_id, id)
) ENGINE=InnoDB COMMENT='会话消息';

-- 知识缺口：同一个问题反复答不上来，该补语料而不是改提示词
CREATE TABLE knowledge_gap (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    question_key    VARCHAR(300) NOT NULL COMMENT '归一化后的问题，用于聚类',
    sample_question VARCHAR(500) NOT NULL,
    hit_count       INT          NOT NULL DEFAULT 1,
    reason          VARCHAR(200) NULL,
    dept            VARCHAR(80)  NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT '待处理' COMMENT '待处理/处理中/已补录/无需处理',
    assignee        VARCHAR(50)  NULL,
    note            VARCHAR(500) NULL,
    handled_at      DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gap_question (question_key),
    KEY idx_gap_status (status)
) ENGINE=InnoDB COMMENT='知识缺口与 badcase';

-- =====================================================================
-- 演示样本：治理台一进去就该有东西可看
--
-- 只预置"待办"类数据（反馈与缺口），不预置审计日志与会话：
-- 那两样是使用痕迹，预先塞进去会让人把演示数据误当成真实统计。
-- =====================================================================
INSERT INTO feedback
    (user_id, username, role, question, answer_mode, answer_digest, citation_path,
     type, detail, status, created_at)
VALUES
    (6, '2022001', 'STUDENT', '重修要交费吗', 'generated',
     '在规定的修业年限内，学生可根据每学期的开课计划自主选择重新修读未获得学分的相应课程，并按规定缴交相应的重修费用……',
     '第三部分 学生管理规定 > 第三章 学生学籍管理规定 > 第五节 考核与成绩记载 > 第二十三条',
     'WRONG', '回答只说要交费，没写收费标准在哪里能查到，想补一句指引。',
     '待处理', NOW() - INTERVAL 2 HOUR),
    (5, '2021002', 'STUDENT', '体育课能申请免修吗', 'extractive',
     '思想政治理论课、德育、体育、军事理论、实验、实习、课程设计、毕业设计（论文）等均不得申请免听、间听或免修。',
     '第三部分 学生管理规定 > 第三章 学生学籍管理规定 > 第四节 免修、免听或间听 > 第十九条',
     'USELESS', '我因伤想申请免修，回答只说了不得申请，没说我这种情况该怎么办。',
     '处理中', NOW() - INTERVAL 1 DAY);

INSERT INTO knowledge_gap
    (question_key, sample_question, hit_count, reason, dept, status, note, assignee,
     handled_at, created_at, updated_at)
VALUES
    ('交换生在外校修的学分怎么认定', '交换生在外校修的学分怎么认定', 3,
     '检索到的条款相关度过低', '教务处', '待处理', NULL, NULL, NULL,
     NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),
    ('转专业后已修的学分怎么折算', '转专业后已修的学分怎么折算', 2,
     '知识库里没有检索到与这个问题相关的条款', '教务处', '待处理', NULL, NULL, NULL,
     NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
    ('宿舍网费在哪里交', '宿舍网费在哪里交', 1,
     '检索到的条款相关度过低', '学生社区管理中心', '已补录',
     '宿舍管理规定已补录入库，下次灌入后生效', '教务处', NOW() - INTERVAL 1 DAY,
     NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 1 DAY);
