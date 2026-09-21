package com.iaas.info;

import java.util.List;

public final class InfoDtos {

    private InfoDtos() {
    }

    public record NoticeRow(Long id, String title, String content, String publisher,
                            String targetRole, boolean pinned, String publishedAt) {
    }

    public record MessageRow(Long id, String studentNo, String studentName, String content,
                             String reply, String repliedBy, String repliedAt, String createdAt) {
    }

    public record Reply(List<String> items) {
    }
}
