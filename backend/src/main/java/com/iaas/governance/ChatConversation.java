package com.iaas.governance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 问答会话。多轮上下文按会话聚合在服务端，不靠前端传全部历史。 */
@Data
@TableName("chat_conversation")
public class ChatConversation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private Integer turnCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
