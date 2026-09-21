package com.iaas.application;

import java.util.List;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    /** 提交申请。targetId 与 target 按类型解释：重修是课程，转专业是专业，免听间听是教学班。 */
    public record SubmitRequest(
            String type, Long targetId, String target, String reason, String materials,
            String roomName, Integer roomWeekday, Integer roomStartSection,
            Integer roomEndSection, String roomWeeks) {
    }

    public record Row(
            Long id, String type, String typeText, String status,
            String studentNo, String studentName,
            Long termId, String termName,
            String target, String reason, String materials,
            String roomName, Integer roomWeekday, Integer roomStartSection,
            Integer roomEndSection, String roomWeeks,
            String precheckNote, String reviewer, String reviewNote,
            String reviewedAt, String createdAt) {
    }

    /** 审批：action 取 APPROVE / REJECT。 */
    public record ReviewRequest(String action, String note) {
    }

    public record SubmitResult(Long id, String status, String message, String precheckNote) {
    }

    /**
     * 提交表单里的可选项。不同类型指向的对象不同：
     * 重修是未通过的课程，转专业是专业，免听间听是本学期已选的教学班。
     */
    public record Option(Long id, String label, String note) {
    }

    /**
     * 证明成品。系统不盖章，只把证明内容按可打印的版式排出来，
     * 打印后仍需到教务处盖章——这一点写在证明正文的落款说明里。
     */
    public record Certificate(
            String no, String certName, String kind,
            String studentName, String studentNo, String gender,
            String collegeName, String majorName, String clazzName, Integer grade,
            String issuedDate, String termName,
            String creditSummary, List<String> lines, List<String> notes) {
    }
}
