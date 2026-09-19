-- =====================================================================
-- 智能教务系统 - 知识库结构（第二阶段：RAG）
--
-- 设计要点：
--   1. 文档与切片分表，切片携带完整层级路径，使引用可定位到「第某条」。
--   2. 密级与适用范围存在文档上，检索前据此过滤（PRD 原则 PR2：
--      先鉴权后检索，而不是先召回再由模型忽略）。
--   3. 全文索引用 ngram 分词器，否则中文按空格切词会完全失效。
-- =====================================================================
USE iaas;

DROP TABLE IF EXISTS knowledge_chunk;
DROP TABLE IF EXISTS knowledge_document;

CREATE TABLE knowledge_document (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    title          VARCHAR(200) NOT NULL,
    doc_no         VARCHAR(80)  NULL COMMENT '文号',
    dept           VARCHAR(80)  NOT NULL COMMENT '责任部门',
    effective_date DATE         NULL COMMENT '生效日期，发布门禁要求必填',
    expire_date    DATE         NULL COMMENT '失效日期，空表示长期有效',
    scope          VARCHAR(120) NOT NULL DEFAULT '全校' COMMENT '适用范围',
    visibility     VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/CAMPUS/RESTRICTED',
    status         VARCHAR(20)  NOT NULL DEFAULT '草稿' COMMENT '草稿/待审/生效/已失效',
    auditor        VARCHAR(50)  NULL COMMENT '审核人，发布门禁要求必填',
    source_path    VARCHAR(300) NULL,
    chunk_count    INT          NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kdoc_status (status),
    KEY idx_kdoc_visibility (visibility)
) ENGINE=InnoDB COMMENT='知识库文档';

CREATE TABLE knowledge_chunk (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    document_id    BIGINT       NOT NULL,
    hierarchy_path VARCHAR(300) NOT NULL COMMENT '如 第三部分 > 第三章 > 第五节 > 第二十三条',
    part           VARCHAR(60)  NULL,
    chapter        VARCHAR(60)  NULL,
    section        VARCHAR(60)  NULL,
    article_no     VARCHAR(40)  NULL,
    seq            INT          NOT NULL DEFAULT 0,
    content        TEXT         NOT NULL COMMENT '原文照录，不改写',
    char_count     INT          NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kchunk_doc (document_id),
    KEY idx_kchunk_article (article_no),
    FULLTEXT KEY ft_kchunk_content (content) WITH PARSER ngram
) ENGINE=InnoDB COMMENT='知识库切片';
