package com.iaas.teaching.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 教学班：一门课在某个学期由某位教师开设的一个班次。
 *
 * <p>时间模型为「一周内的连续节次区间 + 单双周标记」，足以支撑选课冲突检测，
 * 又不必引入复杂的排课时刻表结构。
 */
@Data
@TableName("teaching_class")
public class TeachingClass {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long courseId;
    private Long teacherId;
    private Long termId;
    private Integer capacity;
    private Integer enrolled;
    /** 面向专业与年级：专业课表按这两个字段筛，空表示面向全校/不限年级。 */
    private Long majorId;
    private Integer grade;
    private Integer weekday;
    private Integer startSection;
    private Integer endSection;
    private Integer startWeek;
    private Integer endWeek;
    private String weekType;
    private String classroom;
    private String status;

}
