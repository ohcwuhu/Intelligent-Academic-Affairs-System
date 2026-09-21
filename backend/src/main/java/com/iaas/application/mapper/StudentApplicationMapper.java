package com.iaas.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iaas.application.entity.StudentApplication;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StudentApplicationMapper extends BaseMapper<StudentApplication> {

    /**
     * 学生在以往学期未通过的课程，用于"重新修读"申请的可选项。
     *
     * <p>限定 term_id 不等于当前学期：本学期正在修、还没出成绩的课，
     * 不能算"未通过"，否则学生能对正在上的课提交重修申请。
     */
    @Select("""
            SELECT c.id AS courseId, c.code AS courseCode, c.name AS courseName,
                   MIN(e.score) AS lowestScore, MAX(e.score) AS bestScore
            FROM enrollment e
            JOIN teaching_class t ON t.id = e.teaching_class_id
            JOIN course c ON c.id = t.course_id
            WHERE e.student_id = #{studentId}
              AND e.status = 'SELECTED'
              AND e.term_id <> #{currentTermId}
            GROUP BY c.id, c.code, c.name
            HAVING COALESCE(MAX(e.score), 0) < 60
            ORDER BY c.code
            """)
    List<FailedCourse> failedCourses(@Param("studentId") Long studentId,
                                     @Param("currentTermId") Long currentTermId);

    /** 查询结果行：未通过课程。 */
    @Data
    class FailedCourse {
        private Long courseId;
        private String courseCode;
        private String courseName;
        private Double lowestScore;
        private Double bestScore;
    }
}
