package com.iaas.knowledge;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.common.UserContext;
import com.iaas.governance.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 知识库治理：状态机、发布门禁、切片质量校验。
 *
 * <p>对应 PRD 需求 REQ-KB-02（元数据门禁）、REQ-KB-03（审核发布流程）、
 * REQ-KB-04（切片质量校验）。
 *
 * <p>门禁的意义：知识库的质量问题一旦上线，代价是学生照着错规定办事。
 * 宁可让入口难走一点，也不要让不合格的文档偷偷生效。
 */
@Service
public class KnowledgeGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGovernanceService.class);

    /** 切片长度上限。超过说明切分策略失效。 */
    private static final int MAX_CHUNK = 600;
    /** 切片长度下限。低于此值多半是标题碎片，检索时会变成噪音。 */
    private static final int MIN_CHUNK = 20;

    private static final Pattern BAD_CHARS = Pattern.compile("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\ufffd]");
    private static final Pattern ARTICLE_IN_TEXT = Pattern.compile("第[一二三四五六七八九十百]+条");

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final AuditService auditService;

    public KnowledgeGovernanceService(KnowledgeDocumentMapper documentMapper,
                                      KnowledgeChunkMapper chunkMapper,
                                      AuditService auditService) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.auditService = auditService;
    }

    public record ChunkIssue(Long chunkId, String hierarchyPath, String issue) {
    }

    public record ChunkQualityReport(Long documentId, int total, int pass, int issueCount,
                                     List<ChunkIssue> issues) {
    }

    /** 发布前置检查。返回"拦得住的硬伤"清单，为空才允许直接发布。 */
    public List<String> gateIssues(KnowledgeDocument doc) {
        List<String> issues = new ArrayList<>();
        if (doc == null) {
            issues.add("文档不存在");
            return issues;
        }
        if (doc.getTitle() == null || doc.getTitle().isBlank()) {
            issues.add("缺少标题");
        }
        if (doc.getEffectiveDate() == null) {
            issues.add("缺少生效日期");
        }
        if (doc.getDept() == null || doc.getDept().isBlank()) {
            issues.add("缺少责任部门");
        }
        if (doc.getAuditor() == null || doc.getAuditor().isBlank()) {
            issues.add("缺少审核人");
        }
        if (doc.getEffectiveDate() != null && doc.getExpireDate() != null
                && doc.getEffectiveDate().isAfter(doc.getExpireDate())) {
            issues.add("生效日期晚于失效日期");
        }
        long chunks = chunkMapper.selectCount(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getDocumentId, doc.getId()));
        if (chunks == 0) {
            issues.add("没有任何切片");
        }
        return issues;
    }

    /** 草稿 → 待审。 */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        requireStaff();
        KnowledgeDocument doc = mustGet(id);
        if (!"草稿".equals(doc.getStatus())) {
            throw new BizException("只有草稿可以提交审核，当前状态：" + doc.getStatus());
        }
        doc.setStatus("待审");
        documentMapper.updateById(doc);
        auditService.ingest("提交审核：" + doc.getTitle(), 0);
    }

    /**
     * 待审 → 生效。
     *
     * @param force  门禁不通过时是否强行发布
     * @param reason 强行发布必须说明理由，会写进审计
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, boolean force, String reason) {
        requireStaff();
        doPublish(mustGet(id), force, reason);
    }

    /**
     * 系统启动流程使用的发布路径。
     *
     * <p>不能走 {@link #publish}：那条路径要求登录用户是教务人员，
     * 而启动灌入时根本没有请求上下文，会被自己的权限检查挡下来。
     * 这个缺陷实际发生过——文档灌完停在草稿态，检索一条也召不到。
     * 这里不跳过门禁，只是把"谁批准的"记为系统。
     */
    @Transactional(rollbackFor = Exception.class)
    public void publishAsSystem(Long id, boolean force, String reason) {
        doPublish(mustGet(id), force, reason);
    }

    private void doPublish(KnowledgeDocument doc, boolean force, String reason) {
        List<String> issues = gateIssues(doc);
        if (!issues.isEmpty()) {
            if (!force) {
                throw new BizException("发布门禁未通过：" + String.join("；", issues));
            }
            if (reason == null || reason.isBlank()) {
                throw new BizException("强行发布必须说明理由，理由会记入审计");
            }
            log.warn("强行发布知识库文档 id={} 理由={} 未通过项={}",
                    doc.getId(), reason, issues);
            auditService.ingest("强行发布《" + doc.getTitle() + "》，未通过项："
                    + String.join("；", issues) + "，理由：" + reason, 0);
        } else {
            auditService.ingest("发布《" + doc.getTitle() + "》", 0);
        }
        doc.setStatus("生效");
        documentMapper.updateById(doc);
    }

    /** 生效 → 已失效。失效后检索不再召回。 */
    @Transactional(rollbackFor = Exception.class)
    public void expire(Long id, String reason) {
        requireStaff();
        KnowledgeDocument doc = mustGet(id);
        doc.setStatus("已失效");
        doc.setExpireDate(LocalDate.now());
        documentMapper.updateById(doc);
        auditService.ingest("失效《" + doc.getTitle() + "》理由：" + reason, 0);
    }

    /**
     * 切片质量校验。
     *
     * <p>检查项机械可判：长度越界、缺层级路径、结尾被截断、含无效字符、
     * 正文有条号但元数据没记。语义完整性仍要人工抽检，机器判不了。
     */
    public ChunkQualityReport validateChunks(Long documentId) {
        requireStaff();
        mustGet(documentId);
        List<KnowledgeChunk> chunks = chunkMapper.selectList(
                Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getDocumentId, documentId)
                        .orderByAsc(KnowledgeChunk::getSeq));

        List<ChunkIssue> issues = new ArrayList<>();
        for (KnowledgeChunk c : chunks) {
            String path = c.getHierarchyPath() == null ? "" : c.getHierarchyPath();
            String content = c.getContent() == null ? "" : c.getContent();
            if (content.length() > MAX_CHUNK) {
                issues.add(new ChunkIssue(c.getId(), path,
                        "长度 " + content.length() + " 超过上限 " + MAX_CHUNK));
            }
            if (content.length() < MIN_CHUNK) {
                issues.add(new ChunkIssue(c.getId(), path,
                        "长度 " + content.length() + " 低于下限 " + MIN_CHUNK + "，疑似标题碎片"));
            }
            if (path.isBlank()) {
                issues.add(new ChunkIssue(c.getId(), path, "缺少层级路径，引用无法定位"));
            }
            if (BAD_CHARS.matcher(content).find()) {
                issues.add(new ChunkIssue(c.getId(), path, "含无效控制字符，可能是解析乱码"));
            }
            if (!endsCleanly(content)) {
                issues.add(new ChunkIssue(c.getId(), path, "结尾不是完整句，疑似被截断"));
            }
            if (c.getArticleNo() == null && ARTICLE_IN_TEXT.matcher(content).find()) {
                issues.add(new ChunkIssue(c.getId(), path, "正文含条号但元数据未记录条号"));
            }
        }
        int distinct = (int) issues.stream().map(ChunkIssue::chunkId).distinct().count();
        return new ChunkQualityReport(documentId, chunks.size(),
                chunks.size() - distinct, issues.size(), issues);
    }

    /** 中文句子以句号、分号、右括号、引号或字母数字结尾才算收完整。 */
    private boolean endsCleanly(String content) {
        String s = content.strip();
        if (s.isEmpty()) {
            return false;
        }
        char last = s.charAt(s.length() - 1);
        String ok = "。；！？）】”》. ;!?)%";
        return ok.indexOf(last) >= 0 || Character.isLetterOrDigit(last);
    }

    /** 临近失效的文档，用于到期前提醒责任部门确认。 */
    public List<KnowledgeDocument> expiringSoon(int days) {
        LocalDate limit = LocalDate.now().plusDays(days);
        return documentMapper.selectList(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getStatus, "生效")
                .isNotNull(KnowledgeDocument::getExpireDate)
                .le(KnowledgeDocument::getExpireDate, limit)
                .orderByAsc(KnowledgeDocument::getExpireDate));
    }

    private KnowledgeDocument mustGet(Long id) {
        KnowledgeDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("知识库文档");
        }
        return doc;
    }

    private void requireStaff() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可执行知识库治理操作");
        }
    }
}
