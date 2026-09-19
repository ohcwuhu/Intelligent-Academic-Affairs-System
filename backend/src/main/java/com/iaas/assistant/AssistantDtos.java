package com.iaas.assistant;

import java.util.List;
import java.util.Map;

public final class AssistantDtos {

    private AssistantDtos() {
    }

    /**
     * 一条引用。前端据此渲染引用卡片并跳回原文。
     *
     * @param excerpt 原文摘录，逐字取自切片，不做改写
     */
    public record Citation(
            Long chunkId, Long documentId, String documentTitle, String docNo,
            String dept, String effectiveDate, String hierarchyPath,
            String articleNo, String excerpt) {
    }

    /**
     * 一次问答的结果。
     *
     * @param intent     RULE / PERSONAL / PROCESS / OUT_OF_SCOPE / AMBIGUOUS
     * @param mode       generated 模型生成 / extractive 原文摘录 / process 办理指引 /
     *                   tool 结构化查询 / refusal 拒答 / clarify 澄清
     * @param notes      系统对本次回答的说明（数字已核对、模型不可用等）
     * @param data       结构化数据，个人数据类问题才有
     * @param durationMs 本次问答耗时，前端据此判断降级
     */
    public record Answer(
            String intent, String mode, String answer,
            List<Citation> citations, List<String> notes, Map<String, Object> data,
            Long conversationId, long durationMs) {
    }

    public record AskRequest(String question, Long conversationId) {
    }

    /**
     * 能力状态。
     *
     * @param llmReady 生成模型是否可用；为假时走原文摘录
     */
    public record Status(boolean enabled, boolean llmReady, String provider,
                         String model, String message) {
    }
}
