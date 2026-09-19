package com.iaas.governance;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iaas.common.BizException;
import com.iaas.common.PageResult;
import com.iaas.common.R;
import com.iaas.common.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 治理后台：知识缺口与审计日志。
 *
 * <p>审计日志只读，没有修改与删除接口——这是审计本身的要求。
 */
@RestController
@RequestMapping("/api/governance")
public class GovernanceController {

    private final KnowledgeGapService gapService;
    private final FeedbackService feedbackService;
    private final AuditLogMapper auditMapper;

    public GovernanceController(KnowledgeGapService gapService,
                                FeedbackService feedbackService,
                                AuditLogMapper auditMapper) {
        this.gapService = gapService;
        this.feedbackService = feedbackService;
        this.auditMapper = auditMapper;
    }

    @GetMapping("/overview")
    public R<GovernanceDtos.Overview> overview() {
        requireStaff();
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        long askToday = auditMapper.selectCount(Wrappers.<AuditLog>lambdaQuery()
                .eq(AuditLog::getEventType, "ASK")
                .ge(AuditLog::getCreatedAt, dayStart));
        long blockedToday = auditMapper.selectCount(Wrappers.<AuditLog>lambdaQuery()
                .eq(AuditLog::getEventType, "ASK")
                .eq(AuditLog::getBlocked, 1)
                .ge(AuditLog::getCreatedAt, dayStart));
        long injectionToday = auditMapper.selectCount(Wrappers.<AuditLog>lambdaQuery()
                .eq(AuditLog::getEventType, "INJECTION")
                .ge(AuditLog::getCreatedAt, dayStart));
        List<AuditLog> recent = auditMapper.selectList(Wrappers.<AuditLog>lambdaQuery()
                .eq(AuditLog::getEventType, "ASK")
                .ge(AuditLog::getCreatedAt, dayStart));
        double avg = recent.stream()
                .filter(a -> a.getDurationMs() != null)
                .mapToInt(AuditLog::getDurationMs).average().orElse(0);
        return R.ok(new GovernanceDtos.Overview(
                feedbackService.pendingCount(), gapService.pendingCount(),
                askToday, blockedToday, injectionToday, Math.round(avg * 10) / 10.0));
    }

    @GetMapping("/gaps")
    public R<PageResult<KnowledgeGap>> gaps(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String status) {
        return R.ok(gapService.page(page, size, status));
    }

    @GetMapping("/gaps/top")
    public R<List<KnowledgeGap>> topGaps(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(gapService.top(limit));
    }

    @PostMapping("/gaps/{id}/handle")
    public R<Void> handleGap(@PathVariable Long id,
                             @RequestBody GovernanceDtos.HandleRequest req) {
        gapService.handle(id, req.status(), req.assignee(), req.note());
        return R.ok();
    }

    /** 审计日志查询。只读。 */
    @GetMapping("/audit")
    public R<PageResult<AuditLog>> audit(@RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "20") long size,
                                         @RequestParam(required = false) String eventType,
                                         @RequestParam(required = false) Boolean blockedOnly) {
        requireStaff();
        var q = Wrappers.<AuditLog>lambdaQuery()
                .eq(eventType != null && !eventType.isBlank(), AuditLog::getEventType, eventType)
                .eq(Boolean.TRUE.equals(blockedOnly), AuditLog::getBlocked, 1)
                .orderByDesc(AuditLog::getId);
        IPage<AuditLog> p = auditMapper.selectPage(new Page<>(page, size), q);
        return R.ok(new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords()));
    }

    private void requireStaff() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可访问治理后台");
        }
    }
}
