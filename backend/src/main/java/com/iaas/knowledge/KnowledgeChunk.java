package com.iaas.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库切片。
 *
 * <p>切分边界是「条」而不是固定字数：教务规章的语义单元就是条款，
 * 按字数硬切会把一个条件句切成两半，检索回来谁都不完整。
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private String hierarchyPath;
    private String part;
    private String chapter;
    private String section;
    private String articleNo;
    private Integer seq;
    private String content;
    private Integer charCount;
    private LocalDateTime createdAt;
}
