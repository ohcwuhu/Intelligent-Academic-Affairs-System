package com.iaas.knowledge;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.common.R;
import com.iaas.common.UserContext;
import com.iaas.knowledge.RetrievalService.RetrievedChunk;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 知识库查询与治理。
 *
 * <p>查询对所有登录用户开放（引用原文要能点开），治理操作仅限教务侧。
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeIngestService ingestService;
    private final RetrievalService retrievalService;
    private final KnowledgeGovernanceService governanceService;
    /** 文档列表。只返回当前身份有权看到的文档。 */
    @GetMapping("/documents")
    public R<List<KnowledgeDocument>> documents() {
        UserContext.require();
        return R.ok(retrievalService.allowedDocuments());
    }

    /** 切片详情，供引用卡片跳回原文。 */
    @GetMapping("/chunks/{id}")
    public R<KnowledgeChunk> chunk(@PathVariable Long id) {
        UserContext.require();
        KnowledgeChunk c = chunkMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("原文切片");
        }
        // 权限校验不能只靠前端不显示：这里按文档密级再判一次
        boolean allowed = retrievalService.allowedDocuments().stream()
                .anyMatch(d -> d.getId().equals(c.getDocumentId()));
        if (!allowed) {
            throw BizException.forbidden("无权查看该文档");
        }
        return R.ok(c);
    }

    /** 检索自测接口，用于调参与评测，不参与问答流程。 */
    @GetMapping("/search")
    public R<List<RetrievedChunk>> search(@RequestParam String q,
                                          @RequestParam(defaultValue = "5") int limit) {
        UserContext.require();
        return R.ok(retrievalService.retrieve(q, Math.min(limit, 20)));
    }

    /** 重建索引（仅教务侧）。 */
    @PostMapping("/reingest")
    public R<Integer> reingest() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可重建知识库索引");
        }
        int n = ingestService.ingestFromConfiguredPath();
        Long docId = ingestService.lastIngestedDocumentId();
        // 灌入是"先删旧文档、再建草稿"，不接着发布的话，
        // 一次重建就让知识库里一份生效依据都不剩，问答整体变成拒答。
        // 这个坑实测踩过：重建后 stats 的 documents 从 1 变 0，问什么都是"没有相关条款"。
        // 同一份语料重新解析，风险与上一版相同，所以沿用生效状态；
        // 理由进审计，谁在什么时候重建、依据什么，都留痕。
        if (docId != null) {
            governanceService.publishAsSystem(docId, true,
                    "重建索引：与上一版同一份语料，沿用原生效状态");
        }
        return R.ok(n);
    }

    /** 知识库概览。 */
    @GetMapping("/stats")
    public R<Stats> stats() {
        UserContext.require();
        long docs = documentMapper.selectCount(
                Wrappers.<KnowledgeDocument>lambdaQuery().eq(KnowledgeDocument::getStatus, "生效"));
        long chunks = chunkMapper.selectCount(Wrappers.<KnowledgeChunk>lambdaQuery());
        return R.ok(new Stats(docs, chunks));
    }

    /** 发布门禁预检：看看这份文档现在能不能发布。 */
    @GetMapping("/documents/{id}/gate")
    public R<List<String>> gate(@PathVariable Long id) {
        UserContext.require();
        return R.ok(governanceService.gateIssues(documentMapper.selectById(id)));
    }

    /** 切片质量校验。 */
    @GetMapping("/documents/{id}/chunk-quality")
    public R<KnowledgeGovernanceService.ChunkQualityReport> chunkQuality(@PathVariable Long id) {
        return R.ok(governanceService.validateChunks(id));
    }

    /** 草稿 → 待审。 */
    @PostMapping("/documents/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        governanceService.submit(id);
        return R.ok();
    }

    /** 待审 → 生效。门禁不通过默认拒绝；force=true 并给出理由可强行发布。 */
    @PostMapping("/documents/{id}/publish")
    public R<Void> publish(@PathVariable Long id,
                           @RequestParam(defaultValue = "false") boolean force,
                           @RequestParam(required = false) String reason) {
        governanceService.publish(id, force, reason);
        return R.ok();
    }

    /** 生效 → 已失效。失效后检索不再召回。 */
    @PostMapping("/documents/{id}/expire")
    public R<Void> expire(@PathVariable Long id,
                          @RequestParam(required = false) String reason) {
        governanceService.expire(id, reason);
        return R.ok();
    }

    /** 临近失效的文档，用于到期前提醒责任部门确认。 */
    @GetMapping("/documents/expiring")
    public R<List<KnowledgeDocument>> expiring(@RequestParam(defaultValue = "30") int days) {
        UserContext.require();
        return R.ok(governanceService.expiringSoon(Math.min(Math.max(days, 1), 365)));
    }

    public record Stats(long documents, long chunks) {
    }
}
