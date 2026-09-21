package com.iaas.classroom;

import java.util.List;

public final class ClassroomDtos {

    private ClassroomDtos() {
    }

    /** 一个教室在某段时间的占用情况。 */
    public record Occupancy(
            String classroom, Integer weekday, String weekdayText,
            Integer startSection, Integer endSection, String sectionText,
            String courseName, String courseCode, String teachingClassCode,
            String teacherName, Integer startWeek, Integer endWeek, String weekType) {
    }

    /**
     * 某个时段的全景：谁被占、谁空着。
     *
     * @param freeRooms 该时段没有排课的教室（取自系统里出现过的教室）
     */
    public record Slot(Long termId, String termName, Integer weekday, String weekdayText,
                       Integer startSection, Integer endSection,
                       List<Occupancy> busy, List<String> freeRooms) {
    }
}
