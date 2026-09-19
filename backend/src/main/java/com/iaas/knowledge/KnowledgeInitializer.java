package com.iaas.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时按需灌入语料。
 *
 * <p>只在知识库为空时执行，避免每次重启都重切一遍。
 * 语料缺失不会让应用启动失败，只会把问答降级为「无可用依据」。
 */
@Component
public class KnowledgeInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeInitializer.class);

    private final KnowledgeIngestService ingestService;
    private final KnowledgeGovernanceService governanceService;

    @Value("${iaas.knowledge.auto-ingest:true}")
    private boolean autoIngest;

    public KnowledgeInitializer(KnowledgeIngestService ingestService,
                                KnowledgeGovernanceService governanceService) {
        this.ingestService = ingestService;
        this.governanceService = governanceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoIngest) {
            return;
        }
        try {
            if (!ingestService.isEmpty()) {
                log.info("知识库已有生效文档，跳过自动灌入");
                return;
            }
            int n = ingestService.ingestFromConfiguredPath();
            // 走一遍正式发布流程：门禁会因为缺少审核人而拦住，
            // 演示环境带理由强行发布，风险接受声明进入审计。
            Long docId = ingestService.lastIngestedDocumentId();
            if (docId != null) {
                try {
                    governanceService.publishAsSystem(docId, true,
                            "演示环境自动灌入：元数据未经教务处确认，仅用于教学演示，不得作为办事依据");
                } catch (Exception e) {
                    log.warn("自动发布未完成：{}", e.getMessage());
                }
            }
            log.info("知识库自动灌入完成，共 {} 片切片", n);
        } catch (Exception e) {
            log.warn("知识库自动灌入未完成：{}。问答将返回「无可用依据」。", e.getMessage());
        }
    }
}
