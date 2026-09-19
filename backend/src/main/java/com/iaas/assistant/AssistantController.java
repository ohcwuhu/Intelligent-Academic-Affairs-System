package com.iaas.assistant;

import com.iaas.common.R;
import com.iaas.common.UserContext;
import com.iaas.governance.ChatConversation;
import com.iaas.governance.ChatMessage;
import com.iaas.governance.ConversationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智能问答（RAG）。
 *
 * <p>能力边界写在返回结果里，而不是靠界面文案暗示：
 * 每条回答都带 intent（意图）与 mode（产出方式），前端据此如实呈现
 * 「模型生成」「原文摘录」「你自己的数据」「无法回答」。
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final LlmClient llmClient;
    private final ConversationService conversationService;

    @Value("${iaas.assistant.enabled:true}")
    private boolean enabled;

    public AssistantController(AssistantService assistantService, LlmClient llmClient,
                              ConversationService conversationService) {
        this.assistantService = assistantService;
        this.llmClient = llmClient;
        this.conversationService = conversationService;
    }

    /** 能力状态。前端据此决定入口文案，并如实告诉用户当前是哪种模式。 */
    @GetMapping("/status")
    public R<AssistantDtos.Status> status() {
        UserContext.require();
        String message = !enabled
                ? "智能问答未启用"
                : (llmClient.isEnabled()
                        ? "已接入生成模型，回答附带原文引用"
                        : "已启用知识库检索，当前无生成模型，回答直接给出原文");
        return R.ok(new AssistantDtos.Status(enabled, llmClient.isEnabled(),
                llmClient.providerName(), llmClient.modelName(), message));
    }

    @PostMapping("/ask")
    public R<AssistantDtos.Answer> ask(@RequestBody AssistantDtos.AskRequest req) {
        UserContext.require();
        if (!enabled) {
            return R.fail(503, "智能问答未启用");
        }
        if (req == null || req.question() == null || req.question().isBlank()) {
            return R.fail(400, "请输入要问的问题");
        }
        if (req.question().length() > 200) {
            return R.fail(400, "问题太长了，请精简到 200 字以内");
        }
        return R.ok(assistantService.ask(req.question().strip(), req.conversationId()));
    }

    /** 我的会话列表。 */
    @GetMapping("/conversations")
    public R<List<ChatConversation>> conversations() {
        return R.ok(conversationService.myConversations());
    }

    /** 某个会话的完整消息，仅本人可读。 */
    @GetMapping("/conversations/{id}")
    public R<List<ChatMessage>> messages(@PathVariable Long id) {
        return R.ok(conversationService.messages(id));
    }
}
