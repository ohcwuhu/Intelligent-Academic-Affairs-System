package com.iaas.textbook;

import java.math.BigDecimal;
import java.util.List;

public final class TextbookDtos {

    private TextbookDtos() {
    }

    /** 学生视角的一行：本学期这门课的教材 + 我订没订。 */
    public record StudentRow(
            Long textbookId, Long teachingClassId, String teachingClassCode,
            String courseName, String courseCode,
            String title, String author, String publisher, String isbn, BigDecimal price,
            String note, boolean ordered) {
    }

    public record MyTextbooks(List<StudentRow> rows, int orderedCount,
                              BigDecimal orderedAmount, BigDecimal totalAmount) {
    }

    public record SaveRequest(Long id, Long teachingClassId, String title, String author,
                              String publisher, String isbn, BigDecimal price, String note) {
    }
}
