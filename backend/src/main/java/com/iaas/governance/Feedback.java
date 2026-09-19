package com.iaas.governance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户反馈。三态：有用 / 没用 / 内容有误；后两者进入处理闭环。 */
@Data
@TableName("feedback")
public class Feedback {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String role;
    private String question;
    private String answerMode;
    private String answerDigest;
    private String citationPath;
    private String type;
    private String detail;
    private String status;
    private String handler;
    private String handleNote;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
}
