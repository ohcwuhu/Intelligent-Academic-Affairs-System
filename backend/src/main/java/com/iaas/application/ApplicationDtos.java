package com.iaas.application;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    /** 提交申请。targetId 与 target 按类型解释：重修是课程，转专业是专业，免听间听是教学班。 */
    public record SubmitRequest(
            String type, Long targetId, String target, String reason, String materials) {
    }

    public record Row(
            Long id, String type, String typeText, String status,
            String studentNo, String studentName,
            Long termId, String termName,
            String target, String reason, String materials,
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
}
