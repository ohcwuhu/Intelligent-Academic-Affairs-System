package com.iaas.governance;

public final class GovernanceDtos {

    private GovernanceDtos() {
    }

    /**
     * 提交反馈。
     *
     * @param type USEFUL 有用 / USELESS 没用 / WRONG 内容有误
     */
    public record FeedbackRequest(String question, String type, String detail,
                                  String answerMode, String answerDigest, String citationPath) {
    }

    public record HandleRequest(String status, String assignee, String note) {
    }

    /** 后台总览。 */
    public record Overview(long pendingFeedback, long pendingGap,
                           long askToday, long blockedToday, long injectionToday,
                           double avgDurationMs) {
    }
}
