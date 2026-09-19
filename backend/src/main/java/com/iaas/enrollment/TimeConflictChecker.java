package com.iaas.enrollment;

import com.iaas.teaching.entity.TeachingClass;

import java.util.Objects;

/**
 * 上课时间冲突判定。
 *
 * <p>本判定必须由确定性算法给出，不允许交给大模型推断（PRD 原则 PR1）。
 * 四道判断依次为：
 * <ol>
 *   <li>不同星期不冲突；</li>
 *   <li>节次区间不相交不冲突；</li>
 *   <li>周次区间不相交不冲突；</li>
 *   <li>全周与单/双周必相交；单周与双周互补不冲突；单周与单周相交。</li>
 * </ol>
 *
 * <p>第四条是最容易被漏掉的一条：操作系统（单周）与人工智能基础（双周）
 * 时间重叠，但它们从不在同一个自然周同时上课，判成冲突就是误报。
 */
public final class TimeConflictChecker {

    public static final String WEEK_ALL = "ALL";
    public static final String WEEK_ODD = "ODD";
    public static final String WEEK_EVEN = "EVEN";

    private TimeConflictChecker() {
    }

    public static boolean conflicts(TeachingClass a, TeachingClass b) {
        if (a == null || b == null) {
            return false;
        }
        if (!Objects.equals(a.getWeekday(), b.getWeekday())) {
            return false;
        }
        if (b.getStartSection() > a.getEndSection() || a.getStartSection() > b.getEndSection()) {
            return false;
        }
        if (b.getStartWeek() > a.getEndWeek() || a.getStartWeek() > b.getEndWeek()) {
            return false;
        }
        return weeksOverlap(a.getWeekType(), b.getWeekType());
    }

    /** 单双周是否可能在同一个自然周同时上课。 */
    public static boolean weeksOverlap(String typeA, String typeB) {
        String x = normalize(typeA);
        String y = normalize(typeB);
        if (WEEK_ALL.equals(x) || WEEK_ALL.equals(y)) {
            return true;
        }
        return x.equals(y);
    }

    private static String normalize(String type) {
        return type == null || type.isBlank() ? WEEK_ALL : type.trim().toUpperCase();
    }

    /** 人类可读的时间描述，如「周三 3-4节 1-16周(单周)」。 */
    public static String describe(TeachingClass tc) {
        StringBuilder sb = new StringBuilder();
        sb.append(weekdayText(tc.getWeekday())).append(' ').append(tc.getStartSection());
        if (!Objects.equals(tc.getStartSection(), tc.getEndSection())) {
            sb.append('-').append(tc.getEndSection());
        }
        sb.append("节 ").append(tc.getStartWeek()).append('-').append(tc.getEndWeek()).append('周');
        String type = normalize(tc.getWeekType());
        if (WEEK_ODD.equals(type)) {
            sb.append("(单周)");
        } else if (WEEK_EVEN.equals(type)) {
            sb.append("(双周)");
        }
        return sb.toString();
    }

    public static String weekdayText(Integer weekday) {
        if (weekday == null) {
            return "时间待定";
        }
        String[] names = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return weekday >= 1 && weekday <= 7 ? names[weekday] : "时间待定";
    }
}
