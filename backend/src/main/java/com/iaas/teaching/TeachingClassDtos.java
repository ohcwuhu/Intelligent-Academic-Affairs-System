package com.iaas.teaching;

import java.math.BigDecimal;
import java.util.List;

public final class TeachingClassDtos {

    private TeachingClassDtos() {
    }

    public record TeachingClassVO(
            Long id, String code,
            Long courseId, String courseCode, String courseName,
            BigDecimal credit, String courseType,
            Long teacherId, String teacherName,
            Long termId, String termName,
            Integer capacity, Integer enrolled, Integer remaining,
            Long majorId, String majorName, Integer grade,
            Integer weekday, Integer startSection, Integer endSection,
            Integer startWeek, Integer endWeek, String weekType,
            String classroom, String status, String timeText) {
    }

    public record SaveRequest(
            Long id, String code, Long courseId, Long teacherId, Long termId,
            Integer capacity, Integer weekday, Integer startSection, Integer endSection,
            Integer startWeek, Integer endWeek, String weekType,
            String classroom, String status, Long majorId, Integer grade) {
    }

    /**
     * 排课冲突。
     *
     * @param type TEACHER 教师撞课 / CLASSROOM 教室被占用
     */
    public record ScheduleConflict(String type, String conflictWith,
                                   String timeText, Long teachingClassId) {
    }

    public record SaveResult(Long id, List<ScheduleConflict> conflicts) {
    }
}
