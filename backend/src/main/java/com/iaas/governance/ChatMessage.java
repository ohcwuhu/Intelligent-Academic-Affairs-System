package com.iaas.governance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String intent;
    private String mode;
    private String citationPaths;
    private LocalDateTime createdAt;
}
