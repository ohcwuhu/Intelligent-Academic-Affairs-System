package com.iaas.assistant;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数值接地校验。
 *
 * <p>PRD 的第一条产品原则是「数值不生成」：学分、绩点、门数、时限这些数字
 * 要么来自后端计算，要么逐字来自原文。规则类问答走的是后者，所以模型写出的
 * 每个数字都必须能在检索到的条款里找到出处。这一条机械可查，因此放在回答
 * 发出之前当闸门用。
 *
 * <p>实现上踩过四个坑，都写在这里免得重犯：
 * <ol>
 *   <li><b>不能用子串匹配。</b>直接判断 context 是否含 "3"，会被 "30分钟" 命中，
 *       于是模型编造的 3 也能过。必须两边都抽成数字集合再比。</li>
 *   <li><b>中文数字要归一化。</b>原文写「三门」，模型写「3 门」，语义相同；
 *       不归一化会把正常改写误判成编造，把好回答打回去。</li>
 *   <li><b>年份可能带空格。</b>「2026 年」中间的空格会让朴素的相邻字符判断失效。</li>
 *   <li><b>两侧归一化必须一致。</b>允许集合存的是归一化后的值（原文的 1.0 存成 1），
 *       却拿原始的 1.0 去比，会永远匹配不上——这曾导致「什么情况下不准转专业」
 *       的每一次生成都被静默丢弃，用户只看到原文摘录，永远不知道模型其实答对了。</li>
 * </ol>
 */
@Component
public class NumberGroundingChecker {

    /** 阿拉伯数字，含小数。 */
    private static final Pattern ARABIC = Pattern.compile("\\d+(?:\\.\\d+)?");

    /**
     * 中文数字，只在后面跟量词或条款单位时才当数字，
     * 避免把「一般」「一律」「同一」里的「一」当成数字。
     */
    private static final Pattern CHINESE = Pattern.compile(
            "([零一二三四五六七八九十百]+)(?=[条款项门分元年月日个次周天周节章%％])");

    private static final char[] DIGITS = "零一二三四五六七八九".toCharArray();

    public record Result(boolean grounded, List<String> ungrounded) {
    }

    public Result check(String answer, String context) {
        if (answer == null || answer.isBlank()) {
            return new Result(true, List.of());
        }
        Set<String> allowed = extractNumbers(context);
        Set<String> bad = new LinkedHashSet<>();

        Matcher m = ARABIC.matcher(answer);
        while (m.find()) {
            String raw = m.group();
            int end = m.end();
            String n = normalize(raw);
            if (allowed.contains(n)) {
                continue;
            }
            if (isListOrdinal(answer, m.start()) || isYearLike(answer, end)) {
                continue;
            }
            bad.add(raw);
        }
        return new Result(bad.isEmpty(), List.copyOf(bad));
    }

    /** 从一段文本里抽出全部数字，中文数字折算成阿拉伯数字。 */
    private Set<String> extractNumbers(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) {
            return out;
        }
        Matcher a = ARABIC.matcher(text);
        while (a.find()) {
            out.add(normalize(a.group()));
        }
        Matcher c = CHINESE.matcher(text);
        while (c.find()) {
            int v = chineseToInt(c.group(1));
            if (v > 0) {
                out.add(String.valueOf(v));
            }
        }
        return out;
    }

    /** 去掉小数尾零与前导零，让 3.0、3.00 与 3 视为同一个数。 */
    private String normalize(String n) {
        if (n.contains(".")) {
            String t = n.replaceAll("0+$", "").replaceAll("\\.$", "");
            return t.isEmpty() ? "0" : t;
        }
        String t = n.replaceFirst("^0+(?=\\d)", "");
        return t.isEmpty() ? "0" : t;
    }

    /** 中文数字转整数，支持一到九十九与「一百」这类写法。 */
    static int chineseToInt(String cn) {
        if (cn == null || cn.isEmpty()) {
            return -1;
        }
        if (cn.equals("十")) {
            return 10;
        }
        int total = 0;
        int section = 0;
        for (char ch : cn.toCharArray()) {
            if (ch == '百') {
                section = (section == 0 ? 1 : section) * 100;
                total += section;
                section = 0;
                continue;
            }
            int d = digitOf(ch);
            if (d < 0) {
                return -1;
            }
            section = d;
        }
        return total + section;
    }

    private static int digitOf(char ch) {
        for (int i = 0; i < DIGITS.length; i++) {
            if (DIGITS[i] == ch) {
                return i;
            }
        }
        return -1;
    }

    /** 行首编号、列表符号后的编号不算事实性数字。 */
    private boolean isListOrdinal(String text, int start) {
        int lineStart = text.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
        String prefix = text.substring(lineStart, start).strip();
        return prefix.isEmpty() || prefix.matches("[-\\*\\(\\)\uff08\uff09\u00b7\u3001]+");
    }

    /** 「2026 年」这类带空格的年份视为时间标识，不是统计数字。 */
    private boolean isYearLike(String text, int end) {
        int i = end;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\u3000')) {
            i++;
        }
        return i < text.length() && text.charAt(i) == '\u5e74';
    }
}
