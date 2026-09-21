package com.iaas.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生申请单。
 *
 * <p>免听间听、重修、转专业这类"办事"在教务处都是同一件事：
 * 学生按规则提交、教务按规则审批、过程留痕。所以用一张表统一承载，
 * 用 {@code type} 区分事项，而不是每种事项各建一张只有几列的表。
 *
 * <p>为什么不是"提交即生效"：手册对这些事项都写了条件与时限
 * （免听须在第一周内提出、转专业受名额与绩点限制），
 * 系统能判断的部分在提交时先拦一道，判断不了的留给教务处人工审。
 */
@Data
@TableName("student_application")
public class StudentApplication {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** ON_EXEMPT（免听间听）/ RETAKE（重修）/ TRANSFER_MAJOR（转专业）/ CERTIFICATE（证明打印） */
    private String type;
    private Long studentId;
    private Long termId;
    /** 事项指向的对象：课程、教学班或专业，按类型解释 */
    private String target;
    private Long targetId;
    private String reason;
    /** 材料说明：手册要求的证明材料，学生自己写清楚带了什么 */
    private String materials;
    /** 待审 / 已通过 / 已驳回 / 已撤回 */
    private String status;
    private String reviewer;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    /** 系统在提交时自动判定的结论，供审批人参考 */
    private String precheckNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
