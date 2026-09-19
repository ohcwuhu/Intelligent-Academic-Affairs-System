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
