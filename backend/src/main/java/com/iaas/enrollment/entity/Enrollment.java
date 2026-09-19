package com.iaas.enrollment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 选课记录，同时承载成绩。
 *
 * <p>把成绩并入选课记录而不是单开一张表：二者是一对一关系
 * （一名学生在一个教学班只有一条记录），拆表只会带来无意义的 JOIN。
 */
@Data
@TableName("enrollment")
public class Enrollment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long teachingClassId;
    private Long termId;
    private String status;
    private BigDecimal score;
    private String scoreStatus;
    private BigDecimal gradePoint;
    private LocalDateTime selectedAt;
    private LocalDateTime updatedAt;

}
