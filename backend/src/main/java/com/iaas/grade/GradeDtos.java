package com.iaas.grade;

import java.math.BigDecimal;
import java.util.List;

public final class GradeDtos {

    private GradeDtos() {
    }

    /** 一项分项成绩。 */
    public record Component(Long id, Long enrollmentId, String item,
                            BigDecimal weight, BigDecimal score) {
    }

    /** 教师端：某个学生的分项成绩。 */
    public record StudentComponents(Long enrollmentId, String studentNo, String studentName,
                                    BigDecimal totalScore, List<Component> items) {
    }

    /** 学生端：一门前课的构成。 */
    public record CourseComponents(String courseCode, String courseName, String termName,
                                   BigDecimal totalScore, String scoreStatus,
                                   List<Component> items) {
    }

    /** 保存请求：一次提交一个学生的若干分项。 */
    public record SaveComponent(Long enrollmentId, String item, BigDecimal weight, BigDecimal score) {
    }
}
