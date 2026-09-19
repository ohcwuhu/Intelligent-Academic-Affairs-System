package com.iaas.assistant;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 提示词注入防护（PRD 需求 REQ-QA-09）。
 *
 * <p>威胁模型分两个入口，必须分开处理：
 * <ol>
 *   <li><b>用户输入</b>：直接诱导模型越权或改变角色，识别到就拒答并记安全事件；</li>
 *   <li><b>知识库文档</b>：文件正文里藏一句"忽略以上规则"，被检索回来时就成了上下文的一部分。
 *       这一路更危险，因为它不需要攻击者接触系统，只要往公开文件里塞一句就行。</li>
 * </ol>
 *
 * <p>对文档这一路，做法不是"检测到就拒绝整篇"，而是把命中的片段<strong>中和</strong>掉：
 * 用不可执行的分隔标记包起来，并在系统提示里声明它是资料不是指令。
 * 单纯拒答会让一份被污染的公开文件永远无法作答，代价过大。
 */
@Component
public class InjectionGuard {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("(忽略|无视|忘记)(以上|上述|之前|前面|所有)(的)?(规则|指令|提示|要求|设定)"),
            Pattern.compile("(ignore|disregard|forget)\\s+(all\\s+)?(previous|above|prior|earlier)\\s+(instructions?|rules?|prompts?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(你现在是|从现在开始你是|扮演|假装你是).{0,20}(管理员|系统|开发者|root)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(system|assistant)\\s*[:：]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(输出|打印|告诉我|列出).{0,10}(系统提示|提示词|system\\s*prompt|你的设定)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(绕过|跳过|关闭).{0,8}(权限|鉴权|校验|限制)"),
            Pattern.compile("</?(system|instruction|prompt)>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(泄露|导出|下载).{0,8}(全部|所有).{0,8}(学生|成绩|名单|数据)"));

    /** 通用越权索取句式，即使没有注入关键词也要拦。 */
    private static final List<Pattern> EXFILTRATION = List.of(
            Pattern.compile("(全部|所有|全体|全校).{0,6}(学生|师生|教师).{0,6}(名单|信息|成绩|电话|邮箱)"),
            Pattern.compile("(导出|下载|打包).{0,10}(学生|成绩|名单|数据库)"));

    public record Verdict(boolean malicious, String reason, String matched) {
        public static Verdict clean() {
            return new Verdict(false, null, null);
        }
    }

    public Verdict inspectInput(String question) {
        if (question == null || question.isBlank()) {
            return Verdict.clean();
        }
        for (Pattern p : PATTERNS) {
            var m = p.matcher(question);
            if (m.find()) {
                return new Verdict(true, "检测到疑似指令注入", m.group());
            }
        }
        for (Pattern p : EXFILTRATION) {
            var m = p.matcher(question);
            if (m.find()) {
                return new Verdict(true, "检测到批量索取数据的请求", m.group());
            }
        }
        return Verdict.clean();
    }

    /** 检查检索到的条款正文，返回命中的注入片段；为空表示干净。 */
    public String inspectDocument(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        for (Pattern p : PATTERNS) {
            var m = p.matcher(content);
            if (m.find()) {
                return m.group();
            }
        }
        return null;
    }

    /**
     * 把条款正文包进不可执行的数据区，并在提示里声明它不是指令。
     * 这是纵深防御的第二层：即使检测漏了，模型也被明确告知这段是资料。
     */
    public String wrapAsData(String content) {
        return "<<<资料开始>>>\n" + content + "\n<<<资料结束>>>";
    }
}
