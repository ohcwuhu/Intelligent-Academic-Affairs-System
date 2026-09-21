package com.iaas.program;

import java.math.BigDecimal;
import java.util.List;

public final class ProgramDtos {

    private ProgramDtos() {
    }

    public record ProgramRow(
            Long id, String title, Long majorId, String majorName, Integer grade,
            String degree, String duration, BigDecimal minCredit, String sourceNote,
            String status, String importedAt,
            int moduleCount, int courseCount, BigDecimal courseCreditSum) {
    }

    public record ModuleRow(
            String category, String hoursText, BigDecimal credit, BigDecimal ratio) {
    }

    public record CourseRow(
            Long id, String module, String groupName, String courseName, String courseType,
            String assessType, BigDecimal credit, Integer totalHours, Integer labHours,
            Integer computerHours, Integer termNo, Integer weekHours, String note,
            String required, Long courseId, String courseCode) {
    }

    public record Detail(ProgramRow program, List<ModuleRow> modules, List<CourseRow> courses) {
    }

    /**
     * 模块维度的毕业审核结果。
     *
     * @param earned   该模块已获学分（只算计划内且已通过的课）
     * @param gap      还差多少学分，已修满为 0
     * @param missing  该模块还没通过的课程名，前若干条
     */
    public record ModuleAudit(
            String category, BigDecimal required, BigDecimal earned, BigDecimal gap,
            int planCourses, int passedCourses, List<String> missing) {
    }

    public record Audit(
            Long studentId, String studentNo, String studentName,
            Long programId, String programTitle, String majorName, Integer grade,
            BigDecimal minCredit, BigDecimal earned, BigDecimal gap, boolean complete,
            List<ModuleAudit> modules, List<CourseRow> passedOutsidePlan, List<String> notes) {
    }
}
