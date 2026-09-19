package com.iaas.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 知识库文档。
 *
 * <p>密级与适用范围存在文档层，检索前据此过滤。个人数据绝不进本表。
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String docNo;
    private String dept;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private String scope;
    private String visibility;
    private String status;
    private String auditor;
    private String sourcePath;
    private Integer chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
