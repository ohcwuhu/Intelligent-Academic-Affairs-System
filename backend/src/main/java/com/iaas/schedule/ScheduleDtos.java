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

    /**
     * 专业课表：某个专业某个年级这一学期开出的课。
     *
     * <p>与"我的课表"的区别在数据来源：那是我选了什么，这是这个专业开了什么，
     * 所以它同时也是选课前的参考。
     */
    public record MajorTimetable(
            Long termId, String termName,
            Long majorId, String majorName, Integer grade,
            int courseCount, List<TimetableEntry> entries) {
    }
}
