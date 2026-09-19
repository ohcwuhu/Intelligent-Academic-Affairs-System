package com.iaas.grade;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 绩点与平均学分绩点换算。
 *
 * <p>采用国内高校通用的分段绩点表（4.0 制）：
 * <pre>
 *   90-100 → 4.0     85-89 → 3.7     82-84 → 3.3
 *   78-81  → 3.0     75-77 → 2.7     72-74 → 2.3
 *   68-71  → 2.0     64-67 → 1.5     60-63 → 1.0
 *   60 以下 → 0.0（不计学分）
 * </pre>
 *
 * <p>换算规则集中在这一处，避免同一套规则散落多处导致口径不一致。
 */
public final class GradePointCalculator {

    /** 及格线。低于此分数不计学分、绩点记 0。 */
    public static final BigDecimal PASS_SCORE = new BigDecimal("60");

    private GradePointCalculator() {
    }

    /** 由百分制成绩换算绩点。成绩为空返回 null（表示未录入）。 */
    public static BigDecimal toGradePoint(BigDecimal score) {
        if (score == null) {
            return null;
        }
        double s = score.doubleValue();
        double gp;
        if (s >= 90) {
            gp = 4.0;
        } else if (s >= 85) {
            gp = 3.7;
        } else if (s >= 82) {
            gp = 3.3;
        } else if (s >= 78) {
            gp = 3.0;
        } else if (s >= 75) {
            gp = 2.7;
        } else if (s >= 72) {
            gp = 2.3;
        } else if (s >= 68) {
            gp = 2.0;
        } else if (s >= 64) {
            gp = 1.5;
        } else if (s >= 60) {
            gp = 1.0;
        } else {
            gp = 0.0;
        }
        return BigDecimal.valueOf(gp).setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean isPassed(BigDecimal score) {
        return score != null && score.compareTo(PASS_SCORE) >= 0;
    }

    /** 平均学分绩点 = Σ(绩点 × 学分) / Σ学分。已获学分为 0 时返回 0。 */
    public static BigDecimal gpa(BigDecimal totalWeighted, BigDecimal totalCredit) {
        if (totalCredit == null || totalCredit.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return totalWeighted.divide(totalCredit, 2, RoundingMode.HALF_UP);
    }
}
