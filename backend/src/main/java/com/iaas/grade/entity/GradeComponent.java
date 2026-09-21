package com.iaas.grade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 成绩构成：总评成绩由哪些部分、各占多少、各得多少分。
 *
 * <p>总评仍然是 enrollment.score 那一个数，这张表只解释它是怎么来的。
 * 不把分项塞进 enrollment：一门课的分项数量会变（有的课没有期中），塞进去就得反复加列。
 */
@Data
@TableName("grade_component")
public class GradeComponent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long enrollmentId;
    /** 平时 / 期中 / 期末 */
    private String item;
    /** 占比百分比，如 30 */
    private BigDecimal weight;
    private BigDecimal score;
}
