package com.iaas.governance;

import com.iaas.common.UserContext;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * 审计服务。
 *
 * <p>两条纪律：
 * <ol>
 *   <li>写审计失败不能影响主流程。审计是旁路，不是业务的一环；
 *       这里吞掉异常只记日志，否则审计库抖动会连带问答不可用。</li>
 *   <li>日志表只追加，不提供修改与删除接口。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    /** 问题字段上限，超出截断，避免一条超长输入撑爆审计行。 */
    private static final int MAX_QUESTION = 480;

    private final AuditLogMapper mapper;
    public void ask(String question, String intent, String mode, int hitCount,
                    int citationCount, boolean blocked, String reason, long durationMs) {
        AuditLog entry = base("ASK");
        entry.setQuestion(trim(question));
        entry.setIntent(intent);
        entry.setMode(mode);
        entry.setHitCount(hitCount);
        entry.setCitationCount(citationCount);
        entry.setBlocked(blocked ? 1 : 0);
        entry.setReason(trimReason(reason));
        entry.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
        save(entry);
    }

    /** 记录安全事件，例如提示词注入或越权尝试。 */
    public void security(String eventType, String question, String reason) {
        AuditLog entry = base(eventType);
        entry.setQuestion(trim(question));
        entry.setBlocked(1);
        entry.setReason(trimReason(reason));
        save(entry);
    }

    /** 记录知识库操作（入库、发布、失效）。 */
    public void ingest(String reason, int chunkCount) {
        AuditLog entry = base("INGEST");
        entry.setReason(trimReason(reason));
        entry.setHitCount(chunkCount);
        save(entry);
    }

    /**
     * 记录办事类操作（提交申请、审批、撤回）。
     *
     * <p>理由拼成一句话写进 reason：谁在什么时候把哪张单子办成了什么，
     * 出问题时能顺着这条线查回原始单据。
     */
    public void workflow(String eventType, String subject, String reason) {
        AuditLog entry = base(eventType);
        entry.setQuestion(trim(subject));
        entry.setReason(trimReason(reason));
        save(entry);
    }

    private AuditLog base(String eventType) {
        AuditLog e = new AuditLog();
        e.setEventType(eventType);
        e.setCreatedAt(LocalDateTime.now());
        UserContext.Principal me = UserContext.get();
        if (me != null) {
            e.setUserId(me.userId());
            e.setUsername(me.username());
            e.setRole(me.role());
        }
        return e;
    }

    private void save(AuditLog entry) {
        try {
            mapper.insert(entry);
        } catch (Exception ex) {
            log.warn("写审计日志失败（不影响主流程）：{}", ex.getMessage());
        }
    }

    private static String trim(String s) {
        if (s == null) return null;
        return s.length() <= MAX_QUESTION ? s : s.substring(0, MAX_QUESTION);
    }

    private static String trimReason(String s) {
        if (s == null) return null;
        return s.length() <= 200 ? s : s.substring(0, 200);
    }
}
