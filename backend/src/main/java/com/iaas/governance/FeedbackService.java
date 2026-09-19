package com.iaas.governance;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iaas.common.BizException;
import com.iaas.common.PageResult;
import com.iaas.common.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 反馈闭环。
 *
 * <p>闭环的关键不是"能提交反馈"，而是**处理结果能回流到评测集**：
 * 评测脚本会拉取标记为「已修正」的反馈，把它们作为用例纳入下一轮回归，
 * 没有这一步，反馈就只是一堆没人看的意见。
 */
@Service
public class FeedbackService {

    private static final List<String> TYPES = List.of("USEFUL", "USELESS", "WRONG");

    private final FeedbackMapper feedbackMapper;
    private final KnowledgeGapService gapService;

    public FeedbackService(FeedbackMapper feedbackMapper, KnowledgeGapService gapService) {
        this.feedbackMapper = feedbackMapper;
        this.gapService = gapService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long submit(Feedback req) {
        UserContext.Principal me = UserContext.require();
        if (req.getType() == null || !TYPES.contains(req.getType())) {
            throw new BizException("反馈类型不合法");
        }
        if (req.getQuestion() == null || req.getQuestion().isBlank()) {
            throw new BizException("缺少对应的问题");
        }
        if ("WRONG".equals(req.getType())
                && (req.getDetail() == null || req.getDetail().isBlank())) {
            throw new BizException("标记为内容有误时，请说明错在哪里");
        }
        if (feedbackMapper.selectCount(Wrappers.<Feedback>lambdaQuery()
                .eq(Feedback::getUserId, me.userId())
                .eq(Feedback::getQuestion, req.getQuestion())
                .eq(Feedback::getType, req.getType())) > 0) {
            // 同一个人对同一个问题重复点同一个反馈，按幂等处理
            throw new BizException("这条反馈你已经提交过了");
        }

        Feedback f = new Feedback();
        f.setUserId(me.userId());
        f.setUsername(me.username());
        f.setRole(me.role());
        f.setQuestion(req.getQuestion());
        f.setAnswerMode(req.getAnswerMode());
        f.setAnswerDigest(cut(req.getAnswerDigest(), 480));
        f.setCitationPath(cut(req.getCitationPath(), 280));
        f.setType(req.getType());
        f.setDetail(cut(req.getDetail(), 980));
        // 有用不需要处理，其余进待办
        f.setStatus("USEFUL".equals(req.getType()) ? "无需处理" : "待处理");
        f.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(f);

        if ("WRONG".equals(req.getType()) || "USELESS".equals(req.getType())) {
            gapService.record(req.getQuestion(),
                    "WRONG".equals(req.getType()) ? "用户反馈内容有误" : "用户反馈没有解决问题");
        }
        return f.getId();
    }

    public PageResult<Feedback> page(long page, long size, String status, String type) {
        requireStaff();
        var q = Wrappers.<Feedback>lambdaQuery()
                .eq(status != null && !status.isBlank(), Feedback::getStatus, status)
                .eq(type != null && !type.isBlank(), Feedback::getType, type)
                .orderByAsc(Feedback::getStatus)
                .orderByDesc(Feedback::getCreatedAt);
        IPage<Feedback> p = feedbackMapper.selectPage(new Page<>(page, size), q);
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords());
    }

    /**
     * 已修正的反馈，供评测脚本拉取并纳入回归用例。
     * 这是"反馈 → 评测集"这条闭环的出口。
     */
    public List<Feedback> corrected(int limit) {
        requireStaff();
        return feedbackMapper.selectList(Wrappers.<Feedback>lambdaQuery()
                .eq(Feedback::getStatus, "已修正")
                .orderByDesc(Feedback::getHandledAt)
                .last("limit " + Math.min(Math.max(limit, 1), 200)));
    }

    /** 我的反馈与处理状态，让用户看得到自己的纠错有没有被理会。 */
    public List<Feedback> mine() {
        Long uid = UserContext.require().userId();
        return feedbackMapper.selectList(Wrappers.<Feedback>lambdaQuery()
                .eq(Feedback::getUserId, uid)
                .orderByDesc(Feedback::getCreatedAt)
                .last("limit 50"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, String status, String note) {
        UserContext.Principal me = UserContext.require();
        requireStaff();
        if (!List.of("处理中", "已修正", "无需处理").contains(status)) {
            throw new BizException("处理状态不合法");
        }
        Feedback f = feedbackMapper.selectById(id);
        if (f == null) {
            throw BizException.notFound("反馈");
        }
        f.setStatus(status);
        f.setHandler(me.username());
        f.setHandleNote(cut(note, 480));
        f.setHandledAt(LocalDateTime.now());
        feedbackMapper.updateById(f);
    }

    public long pendingCount() {
        return feedbackMapper.selectCount(
                Wrappers.<Feedback>lambdaQuery().eq(Feedback::getStatus, "待处理"));
    }

    private void requireStaff() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可处理反馈");
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
