package com.iaas.program.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 培养方案里的一门计划课程（实践环节也是这里的行，用 courseType 区分）。 */
@Data
@TableName("program_course")
public class ProgramCourse {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long programId;
    private String module;
    private String groupName;
    private String courseName;
    private String courseType;
    private String assessType;
    private BigDecimal credit;
    private Integer totalHours;
    private Integer labHours;
    private Integer computerHours;
    private Integer termNo;
    private Integer weekHours;
    private String note;
    private String required;
    private Long courseId;
}
