package com.iaas.enrollment;

import java.math.BigDecimal;
import java.util.List;

/**
 * 选课与成绩传输对象。
 *
 * <p>这些对象由服务层在 Java 中组装（不经 MyBatis 结果映射），可以安全使用 record。
 */
public final class EnrollmentDtos {

    private EnrollmentDtos() {
    }

    /** 一条选课记录（含成绩）的完整展示信息。 */
    public record MyCourse(
            Long enrollmentId, Long teachingClassId, String teachingClassCode,
            String courseCode, String courseName, BigDecimal credit, String courseType,
            String teacherName, String termName, String classroom, String timeText,
            Integer weekday, Integer startSection, Integer endSection,
            BigDecimal score, String scoreStatus, BigDecimal gradePoint, String enrollStatus) {
    }

    /**
     * 学分与绩点汇总。
     *
     * @param earnedCredit     已获学分（成绩 ≥ 60 的课程学分之和）
     * @param inProgressCredit 在修学分（已选但未出成绩）
     * @param gpa              平均学分绩点
     */
    public record CreditSummary(
            BigDecimal earnedCredit, BigDecimal inProgressCredit, BigDecimal gpa,
            int passedCourses, int failedCourses, int inProgressCourses) {
    }

    public record ConflictItem(String courseA, String timeA, String courseB, String timeB) {
    }

    /** 选课结果。conflicts 非空表示"已选上，但产生了新的时间冲突"。 */
    public record SelectResult(Long enrollmentId, String message, List<ConflictItem> conflicts) {
    }

    /** 教学班名单中的一行。 */
    public record RosterItem(
            Long enrollmentId, Long studentId, String studentNo, String studentName,
            String clazzName, String majorName,
            BigDecimal score, String scoreStatus, BigDecimal gradePoint, boolean passed) {
    }

    /** 批量录入时的单条成绩。 */
    public record ScoreEntry(Long enrollmentId, BigDecimal score, String scoreStatus) {
    }
}
