package com.iaas.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private BigDecimal credit;
    private Integer hours;
    private String courseType;
    private Long collegeId;
    private String assessType;
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getCredit() { return credit; }
    public void setCredit(BigDecimal credit) { this.credit = credit; }
    public Integer getHours() { return hours; }
    public void setHours(Integer hours) { this.hours = hours; }
    public String getCourseType() { return courseType; }
    public void setCourseType(String courseType) { this.courseType = courseType; }
    public Long getCollegeId() { return collegeId; }
    public void setCollegeId(Long collegeId) { this.collegeId = collegeId; }
    public String getAssessType() { return assessType; }
    public void setAssessType(String assessType) { this.assessType = assessType; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
