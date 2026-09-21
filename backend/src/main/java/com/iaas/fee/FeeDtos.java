package com.iaas.fee;

import java.math.BigDecimal;
import java.util.List;

public final class FeeDtos {

    private FeeDtos() {
    }

    public record Rule(Long id, String item, BigDecimal creditPrice, String note,
                       String effectiveFrom, Integer status) {
    }

    /** 一条应缴费用：哪门课、多少学分、按哪个项目收费、金额多少。 */
    public record BillItem(
            String courseCode, String courseName, BigDecimal credit,
            String item, BigDecimal unitPrice, BigDecimal amount, String reason) {
    }

    /**
     * 学生账单。
     *
     * @param note 口径说明：金额由"学分 × 单价"算得，单价是演示值
     */
    public record Bill(Long studentId, String studentNo, String studentName,
                       Long termId, String termName,
                       List<BillItem> items, BigDecimal total, List<String> notes) {
    }
}
