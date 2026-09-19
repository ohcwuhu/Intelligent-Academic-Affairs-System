package com.iaas.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数值接地校验的测试。
 *
 * <p>这个校验是「数值不生成」原则唯一机械可查的落点，必须证明它真的会拦，
 * 而不是永远通过。拦截能力靠构造反例证明；同时也要证明它不会误伤正常回答
 * ——一个只做对的检查，会把好答案也打回去。下面九条覆盖了这一对张力。
 */
class NumberGroundingCheckerTest {

    private final NumberGroundingChecker checker = new NumberGroundingChecker();

    private static final String CONTEXT = """
            第二十三条 补考、缓考、旷考与重新修读
            （二）缓考：每生每学期申请缓考一般不超过三门课程。
            （三）旷考：考试时迟到30分钟以上，均作旷考处理。
            """;

    @Test
    @DisplayName("原文写「三门」而回答写「3 门」时仍判定为接地")
    void groundedForChineseArabicEquivalence() {
        String answer = "每学期申请缓考不超过 3 门。\n依据：第二十三条（二）";
        // 中文数字与阿拉伯数字要归一化后再比，否则正常的改写会被误判成编造
        assertTrue(checker.check(answer, CONTEXT).grounded());
    }

    @Test
    @DisplayName("回答照抄原文的中文数字时判定为接地")
    void groundedWhenAnswerKeepsOriginalWording() {
        String answer = "每生每学期申请缓考一般不超过三门课程。\n依据：第二十三条（二）";
        assertTrue(checker.check(answer, CONTEXT).grounded());
    }

    @Test
    @DisplayName("原文出现的阿拉伯数字被引用时接地")
    void groundedForArabicNumberPresentInContext() {
        assertTrue(checker.check("考试时迟到30分钟以上即作旷考处理。", CONTEXT).grounded());
    }

    @Test
    @DisplayName("回答编造了原文没有的数字时判定为不接地")
    void ungroundedWhenNumberIsFabricated() {
        var result = checker.check("每学期申请缓考不超过 5 门，重修费为 200 元。", CONTEXT);
        assertFalse(result.grounded());
        assertTrue(result.ungrounded().contains("5"), "应识别出 5");
        assertTrue(result.ungrounded().contains("200"), "应识别出 200");
    }

    @Test
    @DisplayName("数字不能靠子串蒙混：3 不该被 30分钟 命中")
    void doesNotMatchBySubstring() {
        // 这段上下文里只有一个数字 30；回答写 3，不能因为 "30" 含有 "3" 就放行
        String onlyThirty = "（三）旷考：考试时迟到30分钟以上，均作旷考处理。";
        assertFalse(checker.check("旷考后 3 天内必须提交说明。", onlyThirty).grounded());
    }

    @Test
    @DisplayName("小数的归一化在两侧必须一致：1.0 与 1.0 应当匹配")
    void normalizesDecimalsOnBothSides() {
        // 这条用例来自一个真实缺陷：允许集合存的是归一化后的值，
        // 却拿原始值去比，导致「1.0」永远匹配不上，
        // 于是「什么情况下不准转专业」的每一次生成都被误判成编造而丢弃。
        String ctx = "（四）有下列情况之一者，不准予转专业：2．原专业培养计划中已修的课程平均学分绩点小于1.0者。";
        assertTrue(checker.check("平均学分绩点低于 1.0 的学生不准转专业。", ctx).grounded());
    }

    @Test
    @DisplayName("小数补零后也应匹配：1 与 1.00")
    void normalizesTrailingZeros() {
        assertTrue(checker.check("绩点低于 1.00 者不予转专业。", "绩点小于1者，不准予转专业。").grounded());
    }

    @Test
    @DisplayName("行首编号与年份不算事实性数字")
    void ignoresListOrdinalsAndYears() {
        String answer = """
                1. 缓考需要医院证明。
                2. 依据 2026 年修订的规定执行。
                """;
        assertTrue(checker.check(answer, CONTEXT).grounded());
    }

    @Test
    @DisplayName("空回答不报错")
    void handlesEmptyAnswer() {
        assertTrue(checker.check("", CONTEXT).grounded());
        assertTrue(checker.check(null, CONTEXT).grounded());
    }
}
