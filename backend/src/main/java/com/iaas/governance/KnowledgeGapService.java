package com.iaas.governance;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iaas.common.BizException;
import com.iaas.common.PageResult;
import com.iaas.common.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识缺口管理（PRD 需求 REQ-KB-05）。
 *
 * <p>拒答不是终点，是待办。同一类问题问到第五次，该补的是语料，
 * 不是继续调提示词。所以每次拒答与每一条负反馈都在这里聚合计数。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeGapService {

    private static final int KEY_LENGTH = 60;

    private final KnowledgeGapMapper mapper;
    /**
     * 记一次缺口。同一个问题重复出现只累加计数。
     * 用归一化后的问法做聚类键，问法微调（多一个语气词）仍归到同一类。
     */
    @Transactional(rollbackFor = Exception.class)
    public void record(String question, String reason) {
        if (question == null || question.isBlank()) {
            return;
        }
        String key = normalize(question);
        KnowledgeGap exist = mapper.selectOne(Wrappers.<KnowledgeGap>lambdaQuery()
                .eq(KnowledgeGap::getQuestionKey, key).last("limit 1"));
        if (exist != null) {
            exist.setHitCount(exist.getHitCount() + 1);
            // 已补录的缺口再次出现，说明补的内容没解决问题，退回待处理
            if ("已补录".equals(exist.getStatus())) {
                exist.setStatus("待处理");
                exist.setNote("补录后同类问题再次出现，请复核");
            }
            mapper.updateById(exist);
            return;
        }
        KnowledgeGap gap = new KnowledgeGap();
        gap.setQuestionKey(key);
        gap.setSampleQuestion(cut(question, 480));
        gap.setHitCount(1);
        gap.setReason(cut(reason, 190));
        gap.setDept("教务处");
        gap.setStatus("待处理");
        gap.setCreatedAt(LocalDateTime.now());
        mapper.insert(gap);
    }

    public PageResult<KnowledgeGap> page(long page, long size, String status) {
        requireStaff();
        var q = Wrappers.<KnowledgeGap>lambdaQuery()
                .eq(status != null && !status.isBlank(), KnowledgeGap::getStatus, status)
                .orderByDesc(KnowledgeGap::getHitCount)
                .orderByDesc(KnowledgeGap::getUpdatedAt);
        IPage<KnowledgeGap> p = mapper.selectPage(new Page<>(page, size), q);
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords());
    }

    public List<KnowledgeGap> top(int limit) {
        requireStaff();
        return mapper.selectList(Wrappers.<KnowledgeGap>lambdaQuery()
                .eq(KnowledgeGap::getStatus, "待处理")
                .orderByDesc(KnowledgeGap::getHitCount)
                .last("limit " + Math.min(Math.max(limit, 1), 50)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, String status, String assignee, String note) {
        requireStaff();
        if (!List.of("待处理", "处理中", "已补录", "无需处理").contains(status)) {
            throw new BizException("处理状态不合法");
        }
        KnowledgeGap g = mapper.selectById(id);
        if (g == null) {
            throw BizException.notFound("知识缺口");
        }
        g.setStatus(status);
        g.setAssignee(cut(assignee, 48));
        g.setNote(cut(note, 480));
        if (!"待处理".equals(status)) {
            g.setHandledAt(LocalDateTime.now());
        }
        mapper.updateById(g);
    }

    public long pendingCount() {
        return mapper.selectCount(
                Wrappers.<KnowledgeGap>lambdaQuery().eq(KnowledgeGap::getStatus, "待处理"));
    }

    /** 归一化：去掉标点与空白并截断，把问法微调归到同一类。 */
    static String normalize(String question) {
        String s = question.replaceAll("[\\p{Punct}，。？！、；：（）《》“”‘’【】\\s]+", "");
        return s.length() <= KEY_LENGTH ? s : s.substring(0, KEY_LENGTH);
    }

    private void requireStaff() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可查看与处理知识缺口");
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
