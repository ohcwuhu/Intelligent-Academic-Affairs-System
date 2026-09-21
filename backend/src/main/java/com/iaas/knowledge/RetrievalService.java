package com.iaas.knowledge;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.UserContext;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 检索服务。
 *
 * <p>管线顺序不可调换（PRD 需求 REQ-QA-03）：
 * 查询理解 → 权限过滤 → 多路召回 → 融合 → 时效裁决 → 返回候选。
 *
 * <p><b>权限过滤必须在召回之前。</b>若先召回再由模型忽略无权内容，
 * 一次提示词注入就能把不该给的数据带出去；放在召回前，无权内容根本进不了候选集。
 *
 * <p>召回当前有三路：原查询、口语改写后的查询、向量（本机无 embedding 模型，
 * 返回空集）。融合用 RRF 按名次，不按分数——关键词分与向量余弦分不可比，
 * 直接加权需要调参且换模型就失效。
 */
@Service
@RequiredArgsConstructor
public class RetrievalService {

    /** RRF 融合常数，经验值 60，作用是压低头部排名的绝对优势。 */
    private static final int RRF_K = 60;

    /**
     * 教务领域词表。
     *
     * <p>为什么需要它：实测中「重修要交钱吗」的最高相关度是 3.81，
     * 而「学校篮球队招人吗」是 3.89，两者区间重叠，单靠分数阈值分不开。
     * 所以判据是分数与领域词同时满足，而不是只卡一个分。
     */
    private static final List<String> DOMAIN_TERMS = List.of(
            "学籍", "学分", "绩点", "课程", "选课", "必修", "选修", "公选", "培养方案",
            "考试", "考核", "成绩", "补考", "缓考", "旷考", "重修", "重新修读", "免修",
            "毕业", "结业", "肄业", "学位", "论文", "答辩", "实习", "实践",
            "转专业", "转学", "休学", "复学", "退学", "保留学籍", "学制", "修业年限",
            "注册", "报到", "考勤", "请假", "处分", "违纪", "作弊", "奖励", "奖学金",
            "助学", "贷款", "评优", "教学班", "教师", "课堂", "教材", "收费", "学费",
            "双学位", "辅修", "主修", "预警", "劝退", "补授", "开除", "申诉");

    /** 有领域词时的最低相关度。低于此值视为知识库没有覆盖。 */
    private static final double MIN_SCORE_WITH_DOMAIN = 2.0;
    /** 无领域词时的最低相关度，要求高得多，避免靠巧合的字面重合作答。 */
    private static final double MIN_SCORE_WITHOUT_DOMAIN = 8.0;

    /**
     * 口语到教务术语的映射（查询理解环节）。
     *
     * <p>学生不会照着文件用词提问。「挂科」要能命中「不及格、重修」，
     * 「翘课」要能命中「旷课、旷考」。
     */
    private static final Map<String, String> COLLOQUIAL = Map.ofEntries(
            Map.entry("挂科", "不及格 重修"),
            Map.entry("挂了", "不及格 重修"),
            Map.entry("翘课", "旷课 旷考"),
            Map.entry("逃课", "旷课 旷考"),
            Map.entry("抄", "作弊 违纪"),
            Map.entry("被抓", "违纪 处分"),
            Map.entry("交钱", "收费 费用 缴交"),
            Map.entry("开除", "退学 处分"),
            Map.entry("补考", "补考 不及格"),
            // 学生说的是"重修"，手册里写的是"重新修读"。
            // 不补这条的话，"重修需要什么条件"会被第三十九条（双学位证书授予）抢到第一，
            // 因为那条里也有"已修读"，而真正管重修的第四款排到了后面。
            Map.entry("重修", "重新修读"),
            // 「一学期最多能选多少学分」实测只靠字面召回会落到休学、考核方式那几条，
            // 因为"学期""学分"在手冊里到处都是，区分度太低；补上条款原词才排得到第十七条
            Map.entry("多少学分", "修读理论课程学分"),
            Map.entry("学分上限", "修读理论课程学分"));

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    /**
     * 检索命中的一条切片。
     *
     * @param score    RRF 融合分，只用于排序与展示
     * @param rawScore MySQL 原始相关度分，用于判断证据是否充分
     */
    public record RetrievedChunk(
            Long chunkId, Long documentId, String documentTitle, String docNo,
            String dept, String effectiveDate, String hierarchyPath, String articleNo,
            String content, double score, double rawScore, String channel) {
    }

    public List<RetrievedChunk> retrieve(String question, int limit) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        // 第一步：权限过滤，先算出本次检索允许触及的文档集合
        List<KnowledgeDocument> allowed = allowedDocuments();
        if (allowed.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeDocument> allowedById = allowed.stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));

        // 第二步：多路召回。
        // 口语改写单独成路而不是拼进原查询：实测把「违纪 处分」直接拼到
        // 「作弊被抓了会怎么样」后面，原本命中的第六十九条反而不中了。
        // 分开召回再按名次融合，改写只补召回，不动原查询的精度。
        String normalized = normalizeQuery(question);
        String rewritten = rewriteQuery(normalized);
        List<RetrievedChunk> keyword = keywordChannel(normalized, limit * 3, allowedById);
        List<RetrievedChunk> rewrittenHits = rewritten.equals(normalized)
                ? List.of()
                : keywordChannel(rewritten, limit * 3, allowedById);
        List<RetrievedChunk> vector = vectorChannel(question, limit * 3, allowedById);

        // 第三步：融合
        List<RetrievedChunk> fused = fuse(List.of(keyword, rewrittenHits, vector), limit);

        // 第四步：时效裁决。失效文档已在 allowedDocuments 里排除；
        // 同一主题多版本并存时目前靠生效日期排序，冲突呈现见 PRD 需求 REQ-QA-08。
        return fused;
    }

    /**
     * 计算本次用户可以检索到的文档。
     *
     * <p>规则：文档必须生效；密级不高于用户身份；适用范围要覆盖用户。
     */
    public List<KnowledgeDocument> allowedDocuments() {
        UserContext.Principal me = UserContext.get();
        boolean staff = me != null && me.isStaff();

        List<KnowledgeDocument> docs = documentMapper.selectList(
                Wrappers.<KnowledgeDocument>lambdaQuery()
                        .eq(KnowledgeDocument::getStatus, "生效"));
        List<KnowledgeDocument> result = new ArrayList<>();
        for (KnowledgeDocument d : docs) {
            String vis = d.getVisibility() == null ? "PUBLIC" : d.getVisibility();
            if ("RESTRICTED".equals(vis) && !staff) {
                continue;
            }
            String scope = d.getScope() == null ? "全校" : d.getScope();
            if (!"全校".equals(scope) && !staff) {
                // 非全校范围的语料只放行给教务侧，宁可少召回也不越权
                continue;
            }
            result.add(d);
        }
        return result;
    }

    /** 证据是否足以作答。这是拒答策略的判据。 */
    public boolean hasSufficientEvidence(String question, List<RetrievedChunk> hits) {
        if (hits.isEmpty()) {
            return false;
        }
        double top = hits.stream().mapToDouble(RetrievedChunk::rawScore).max().orElse(0);
        boolean domain = hasDomainTerm(question);
        return domain ? top >= MIN_SCORE_WITH_DOMAIN : top >= MIN_SCORE_WITHOUT_DOMAIN;
    }

    static boolean hasDomainTerm(String question) {
        if (question == null) {
            return false;
        }
        return DOMAIN_TERMS.stream().anyMatch(question::contains);
    }

    /**
     * 问题是否落在教务领域内。口语改写后带出领域词的也算，
     * 例如「挂了怎么办」本身没有领域词，但改写会补上"不及格/重修"。
     *
     * <p>判定结果决定了两件不同的事：领域内但没依据是知识缺口，要记进治理台；
     * 领域外是越界提问，拒答即可，记成缺口只会把待办列表灌满无关问题。
     */
    public static boolean inDomain(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return hasDomainTerm(question) || hasDomainTerm(rewriteQuery(normalizeQuery(question)));
    }

    /** 清洗查询串：去掉标点与多余空白，保留全部实词。 */
    static String normalizeQuery(String question) {
        String cleaned = question.replaceAll("[\\p{Punct}，。？！、；：（）《》“”‘’【】\\s]+", " ").strip();
        return cleaned.length() < 2 ? "" : cleaned;
    }

    /** 把口语词扩成教务术语后追加到查询末尾。 */
    static String rewriteQuery(String question) {
        StringBuilder extra = new StringBuilder();
        for (var e : COLLOQUIAL.entrySet()) {
            if (question.contains(e.getKey())) {
                extra.append(' ').append(e.getValue());
            }
        }
        return extra.isEmpty() ? question : question + extra;
    }

    private List<RetrievedChunk> keywordChannel(String query, int limit,
                                                Map<Long, KnowledgeDocument> allowed) {
        if (query.isBlank()) {
            return List.of();
        }
        List<KnowledgeDocumentMapper.ChunkHit> hits = documentMapper.searchByKeyword(query, limit);
        List<RetrievedChunk> out = new ArrayList<>();
        for (KnowledgeDocumentMapper.ChunkHit h : hits) {
            KnowledgeDocument d = allowed.get(h.getDocumentId());
            if (d == null) {
                // 双重保险：SQL 已按密级过滤，这里再按允许集合核一次
                continue;
            }
            double s = h.getScore() == null ? 0 : h.getScore();
            out.add(new RetrievedChunk(
                    h.getId(), d.getId(), d.getTitle(), d.getDocNo(), d.getDept(),
                    d.getEffectiveDate() == null ? null : d.getEffectiveDate().toString(),
                    h.getHierarchyPath(), h.getArticleNo(), h.getContent(), s, s, "keyword"));
        }
        return out;
    }

    /**
     * 向量路。本机没有 embedding 模型，暂时返回空集。
     *
     * <p>接入方式：实现一个 embedding 提供者，对切片建索引后在这里返回候选，
     * 融合逻辑已经就位，不需要改调用方。
     */
    private List<RetrievedChunk> vectorChannel(String question, int limit,
                                              Map<Long, KnowledgeDocument> allowed) {
        return List.of();
    }

    private List<RetrievedChunk> fuse(List<List<RetrievedChunk>> channels, int limit) {
        Map<Long, Double> score = new LinkedHashMap<>();
        Map<Long, Double> bestRaw = new LinkedHashMap<>();
        Map<Long, RetrievedChunk> byId = new LinkedHashMap<>();
        for (List<RetrievedChunk> channel : channels) {
            accumulate(channel, score, bestRaw, byId);
        }
        List<RetrievedChunk> ranked = score.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder()))
                .map(e -> withScore(byId.get(e.getKey()), e.getValue()))
                .toList();

        // 每条通道的头两名先占位，再用融合分补齐、截断。
        // 理由：改写通道存在的意义就是补召回，实测「一学期最多能选多少学分」
        // 在改写通道里第十七条排第二，但它的查询词更专指，融合分算不过两条通道都进前几名的
        // 通用条款，直接被挤出前五——那这条通道就等于白开了。
        // 留位不等于让位：占位后仍按融合分补齐，只是保证每条通道的头部不被整体洗掉。
        Map<Long, RetrievedChunk> out = new LinkedHashMap<>();
        for (List<RetrievedChunk> channel : channels) {
            for (int i = 0; i < Math.min(2, channel.size()); i++) {
                RetrievedChunk c = channel.get(i);
                out.putIfAbsent(c.chunkId(), withScore(c, score.getOrDefault(c.chunkId(), 0.0)));
            }
        }
        for (RetrievedChunk c : ranked) {
            if (out.size() >= limit) {
                break;
            }
            out.putIfAbsent(c.chunkId(), c);
        }
        // 选出来之后要重排：占位顺序是"先到先得"，不代表谁更相关。
        //
        // 排序按"该条在任一路里的最高原始相关度"，融合分只用来判平手。
        // 为什么敢跨通道比原始分：三路召回用的是同一个相关度标准（MySQL ngram），
        // 分数同源可比；RRF 只解决"名次不可比"的问题，不解决"谁最相关"。
        // 实测教训：「一学期最多能选多少学分」的正确答案（第十七条（三），原始分 8.25）
        // 在改写通道里排第一，却因融合分最低被排到最后，摘录时前三条根本看不到它，
        // 答案里也就丢掉了那个"30 学分"。这一步直接决定降级时先给学生看哪一条。
        return out.values().stream()
                .limit(limit)
                .sorted(Comparator
                        .comparingDouble((RetrievedChunk c) -> bestRaw.getOrDefault(c.chunkId(), 0.0))
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(
                                (RetrievedChunk c) -> score.getOrDefault(c.chunkId(), 0.0)).reversed()))
                .toList();
    }

    private void accumulate(List<RetrievedChunk> list, Map<Long, Double> score,
                            Map<Long, Double> bestRaw,
                            Map<Long, RetrievedChunk> byId) {
        for (int i = 0; i < list.size(); i++) {
            RetrievedChunk c = list.get(i);
            byId.putIfAbsent(c.chunkId(), c);
            score.merge(c.chunkId(), 1.0 / (RRF_K + i + 1), Double::sum);
            bestRaw.merge(c.chunkId(), c.rawScore(), Math::max);
        }
    }

    private RetrievedChunk withScore(RetrievedChunk c, double s) {
        return new RetrievedChunk(c.chunkId(), c.documentId(), c.documentTitle(), c.docNo(),
                c.dept(), c.effectiveDate(), c.hierarchyPath(), c.articleNo(), c.content(),
                s, c.rawScore(), c.channel());
    }

    /** 按 ID 取回切片，用于引用溯源。 */
    public List<KnowledgeChunk> chunksByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return chunkMapper.selectBatchIds(ids);
    }
}
