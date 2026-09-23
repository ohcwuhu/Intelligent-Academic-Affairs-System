package com.iaas.governance;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 治理概览的数字口径。
 *
 * <p>原来这段算在控制器里，只有治理后台用得到；工作台也要用同样的数字，
 * 于是提到服务层——两处各算一遍迟早会出现"工作台说 5 条、治理台说 3 条"。
 */
@Service
@RequiredArgsConstructor
public class GovernanceOverviewService {

    private final KnowledgeGapService gapService;
    private final FeedbackService feedbackService;
    private final AuditLogMapper auditMapper;

    public GovernanceDtos.Overview overview() {
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
        return new GovernanceDtos.Overview(
                feedbackService.pendingCount(), gapService.pendingCount(),
                askToday, blockedToday, injectionToday, Math.round(avg * 10) / 10.0);
    }
}
