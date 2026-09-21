package com.iaas.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 考试安排：一个教学班的一场考试。
 *
 * <p>不建"学生级考试表"：学生能看到的考试由本人选课过滤而来，
 * 存两份口径迟早会对不上（学生退了课，考试表里还留着）。
 */
@Data
@TableName("exam")
public class Exam {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teachingClassId;
    private Long termId;
    private String examType;
    private LocalDate examDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String classroom;
    private String seatNo;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
