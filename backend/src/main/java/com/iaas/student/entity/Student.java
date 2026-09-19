package com.iaas.student.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

@TableName("student")
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String studentNo;
    private String name;
    private String gender;
    private LocalDate birthDate;
    private String phone;
    private String email;
    private Long collegeId;
    private Long majorId;
    private Long clazzId;
    private Integer grade;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Long getCollegeId() { return collegeId; }
    public void setCollegeId(Long collegeId) { this.collegeId = collegeId; }
    public Long getMajorId() { return majorId; }
    public void setMajorId(Long majorId) { this.majorId = majorId; }
    public Long getClazzId() { return clazzId; }
    public void setClazzId(Long clazzId) { this.clazzId = clazzId; }
    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
