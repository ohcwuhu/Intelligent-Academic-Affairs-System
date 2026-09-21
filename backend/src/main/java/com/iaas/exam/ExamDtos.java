package com.iaas.exam;

import java.util.List;

public final class ExamDtos {

    private ExamDtos() {
    }

    /**
     * 一场考试。
     *
     * @param timeText   给人看的时间段
     * @param daysAhead  距今天数，已过为负数
     * @param conflictWith 与该场时间重叠的其它考试（同一天同一时段两场），空表示不冲突
     */
    public record Row(
            Long id, Long teachingClassId, String teachingClassCode,
            String courseCode, String courseName, String teacherName,
            Long termId, String termName,
            String examType, String examDate, String startTime, String endTime,
            String timeText, String classroom, String seatNo, String note,
            Long daysAhead, List<String> conflictWith) {
    }

    /** 保存请求。id 为空表示新增。 */
    public record SaveRequest(
            Long id, Long teachingClassId, String examType,
            String examDate, String startTime, String endTime,
            String classroom, String seatNo, String note) {
    }

    /** 保存结果：返回本次发现的冲突，不阻断保存，由教务判断。 */
    public record SaveResult(Long id, List<Conflict> conflicts) {
    }

    /** 冲突项。kind 为 CLASSROOM（教室占用）或 CLASS（同一教学班重复）。 */
    public record Conflict(String kind, String message) {
    }
}
