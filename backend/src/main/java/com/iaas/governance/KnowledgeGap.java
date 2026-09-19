package com.iaas.governance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识缺口。
 *
 * <p>同一个问题反复答不上来，该补的是语料，不是改提示词。
 * 这张表把拒答与负反馈聚成可指派、可跟踪的待办。
 */
@Data
@TableName("knowledge_gap")
public class KnowledgeGap {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String questionKey;
    private String sampleQuestion;
    private Integer hitCount;
    private String reason;
    private String dept;
    private String status;
    private String assignee;
    private String note;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
