package com.iaas.student;

import java.time.LocalDate;

public final class StudentDtos {

    private StudentDtos() {
    }

    /** 学生档案，带学院、专业、班级名称。 */
    public record StudentVO(
            Long id, String studentNo, String name, String gender, LocalDate birthDate,
            String phone, String email,
            Long collegeId, String collegeName,
            Long majorId, String majorName,
            Long clazzId, String clazzName,
            Integer grade, String status) {
    }

    public record SaveRequest(
            Long id, String studentNo, String name, String gender, LocalDate birthDate,
            String phone, String email,
            Long collegeId, Long majorId, Long clazzId, Integer grade, String status) {
    }
}
