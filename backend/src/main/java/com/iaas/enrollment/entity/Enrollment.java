package com.iaas.enrollment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 选课记录，同时承载成绩。
 *
 * <p>把成绩并入选课记录而不是单开一张表：二者是一对一关系
 * （一名学生在一个教学班只有一条记录），拆表只会带来无意义的 JOIN。
 */
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getTeachingClassId() { return teachingClassId; }
    public void setTeachingClassId(Long teachingClassId) { this.teachingClassId = teachingClassId; }
    public Long getTermId() { return termId; }
    public void setTermId(Long termId) { this.termId = termId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getScoreStatus() { return scoreStatus; }
    public void setScoreStatus(String scoreStatus) { this.scoreStatus = scoreStatus; }
    public BigDecimal getGradePoint() { return gradePoint; }
    public void setGradePoint(BigDecimal gradePoint) { this.gradePoint = gradePoint; }
    public LocalDateTime getSelectedAt() { return selectedAt; }
    public void setSelectedAt(LocalDateTime selectedAt) { this.selectedAt = selectedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
