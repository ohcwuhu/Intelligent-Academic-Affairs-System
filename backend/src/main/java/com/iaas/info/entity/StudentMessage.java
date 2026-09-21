package com.iaas.info.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 学生留言与教务回复。 */
@Data
@TableName("student_message")
public class StudentMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private String content;
    private String reply;
    private String repliedBy;
    private LocalDateTime repliedAt;
    private LocalDateTime createdAt;
}
