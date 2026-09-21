package com.iaas.assistant;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 意图分流（PRD 需求 REQ-QA-02）。
 *
 * <p>这是防止"用 RAG 硬答所有问题"的第一道闸门。采用规则优先：
 * 规则能判的绝不交给模型，因为规则确定的场景下模型只带来不确定性。
 * 规则判不出来时交给调用方澄清，而不是猜。
 */
@Component
public class IntentRouter {

    public enum Intent {
        /** 规则事实型：问"是什么/怎么办/能不能"。 */
        RULE,
        /** 个人数据型：问"我的……"，走计算接口，数字由代码算。 */
        PERSONAL,
        /** 办理引导型：明确说"我要办某事"，需要材料清单与办理入口。 */
        PROCESS,
        /** 越界：索要他人信息，或与教务无关。 */
        OUT_OF_SCOPE,
        /** 模糊：缺少关键要素，需要澄清。 */
        AMBIGUOUS
    }

    /**
     * 个人数据的判定方式：提到自己 + 提到某项个人数据。
     *
     * <p>不用固定短语表。一开始写死「我的成绩」「我的绩点」这类说法，
     * 结果「我的平均学分绩点是多少」漏判成规则问题，跑去检索里找答案。
     * 换成组合判断后，问法怎么变都能认出来。
     */
    private static final List<String> PERSONAL_DATA_NOUNS = List.of(
            "成绩", "学分", "绩点", "平均分", "课表", "选课", "成绩单",
            "排名", "学籍", "档案", "已修", "在修", "考试", "考场");

    /**
     * 个人数据的组合判定：先出现"我/自己/本人"，随后提到某项个人数据。
     *
     * <p>光靠名词表会漏「我这学期选了几门课」——问句里没有"选课"这个连续词，
     * 只有"选了几门课"。所以除了名词表，还要认动词与量词的说法。
     */
    private static final Pattern PERSONAL_COMBO = Pattern.compile(
            "(我|自己|本人).{0,8}(成绩|学分|绩点|平均分|课表|选课|选上|选了|已选|在修|修了|"
                    + "几门课|多少门课|哪些课|排名|学籍|档案|成绩单|考试|考场|哪天考)");

    /** 敏感属性：问这些且不是在问自己，一律按越权处理。 */
    private static final List<String> SENSITIVE_ATTRS = List.of(
            "电话", "手机号", "身份证", "邮箱", "住址", "家庭住址", "联系方式", "学号");

    /** 越界：向系统索要他人数据的措辞。 */
    private static final Pattern OTHERS = Pattern.compile(
            "(别人|他人|同学|室友|朋友|某个人|某某|其他人的|他的|她的).{0,6}(成绩|课表|学分|绩点|档案|名单|学号|电话|身份证)");

    private static final Pattern CLASS_LIST = Pattern.compile(
            "(全体|全部|所有|全校).{0,6}(学生|师生|名单|成绩|信息)");

    /** 与教务完全无关的领域。 */
    private static final List<String> OFF_TOPIC = List.of(
            "写一篇", "作文", "论文", "代码", "编程", "天气", "股票", "菜谱", "翻译", "讲个笑话");

    private static final List<String> RULE_MARKERS = List.of(
            "怎么", "如何", "能不能", "可以吗", "是否", "什么条件", "多久", "几门",
            "多少", "规定", "流程", "材料", "标准", "要求", "限制", "允许");

    /** 办理引导型的标志：用户不是在问规定，而是在说要办一件事。 */
    private static final List<String> PROCESS_MARKERS = List.of(
            "我要办", "我想办", "怎么办理", "如何办理", "办理流程", "需要哪些材料",
            "需要什么材料", "去哪办", "在哪里办", "提交什么", "申请表在哪",
            "我要申请", "我想申请", "我要提交");

    public Intent route(String question) {
        if (question == null || question.isBlank()) {
            return Intent.AMBIGUOUS;
        }
        String q = question.strip();

        // 顺序有讲究：越界优先于个人数据。
        // "帮我查一下我室友的成绩"同时命中"我的成绩"与他人指代，
        // 若先判个人数据就会把它当成本人查自己放过去。
        if (OTHERS.matcher(q).find() || CLASS_LIST.matcher(q).find()) {
            return Intent.OUT_OF_SCOPE;
        }
        if (OFF_TOPIC.stream().anyMatch(q::contains)) {
            return Intent.OUT_OF_SCOPE;
        }
        if (mentionsSelf(q)
                && (PERSONAL_COMBO.matcher(q).find()
                || PERSONAL_DATA_NOUNS.stream().anyMatch(q::contains))) {
            return Intent.PERSONAL;
        }
        // 要别人的联系方式、身份证之类的，不管问法如何都拦掉
        if (!mentionsSelf(q) && SENSITIVE_ATTRS.stream().anyMatch(q::contains)) {
            return Intent.OUT_OF_SCOPE;
        }
        if (PROCESS_MARKERS.stream().anyMatch(q::contains)) {
            return Intent.PROCESS;
        }
        if (RULE_MARKERS.stream().anyMatch(q::contains)) {
            return Intent.RULE;
        }
        // 短到没有任何线索才算模糊
        if (q.length() <= 4) {
            return Intent.AMBIGUOUS;
        }
        return Intent.RULE;
    }

    private boolean mentionsSelf(String q) {
        return q.contains("我") || q.contains("自己") || q.contains("本人");
    }
}
