package com.iaas.schedule;

import java.math.BigDecimal;
import java.util.List;

public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    /**
     * 课表条目。
     *
     * @param teacherName 学生课表中有值
     * @param className   教师课表中有值（教学班代码）
     */
    public record TimetableEntry(
            String courseName, String courseCode,
            String teacherName, String className,
            String classroom,
            Integer weekday, Integer startSection, Integer endSection,
            String timeText, BigDecimal credit) {
    }

    public record Timetable(Long termId, List<TimetableEntry> entries) {
    }
}
