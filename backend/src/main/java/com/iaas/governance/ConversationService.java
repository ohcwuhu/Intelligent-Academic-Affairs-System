package com.iaas.governance;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.common.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 多轮会话（PRD 需求 REQ-QA-01）。
 *
 * <p>上下文放在服务端按会话聚合，而不是让前端每次把历史全传上来：
 * 后者既浪费带宽，也让"最近 5 轮"这条规则可以被客户端随意绕过。
 */
@Service
public class ConversationService {

    /** 参与指代消解的最近轮数。 */
    private static final int CONTEXT_TURNS = 5;

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;

    public ConversationService(ChatConversationMapper conversationMapper,
                               ChatMessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    /** 取会话；不存在则新建。会话必须属于当前用户。 */
    @Transactional(rollbackFor = Exception.class)
    public Long ensureConversation(Long conversationId, String firstQuestion) {
        UserContext.Principal me = UserContext.require();
        if (conversationId != null) {
            ChatConversation c = conversationMapper.selectById(conversationId);
            if (c == null || !c.getUserId().equals(me.userId())) {
                throw BizException.notFound("会话");
            }
            return c.getId();
        }
        ChatConversation c = new ChatConversation();
        c.setUserId(me.userId());
        c.setTitle(cut(firstQuestion, 40));
        c.setTurnCount(0);
        c.setCreatedAt(LocalDateTime.now());
        conversationMapper.insert(c);
        return c.getId();
    }

    /** 最近若干轮问答，按时间正序，用于指代消解。 */
    public List<String> recentContext(Long conversationId) {
        if (conversationId == null) {
            return List.of();
        }
        List<ChatMessage> msgs = messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getId)
                .last("limit " + (CONTEXT_TURNS * 2)));
        List<String> out = new ArrayList<>();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            ChatMessage m = msgs.get(i);
            out.add(("user".equals(m.getRole()) ? "用户：" : "助手：") + cut(m.getContent(), 200));
        }
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public void append(Long conversationId, String role, String content,
                       String intent, String mode, List<String> citationPaths) {
        if (conversationId == null) {
            return;
        }
        ChatMessage m = new ChatMessage();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content);
        m.setIntent(intent);
        m.setMode(mode);
        m.setCitationPaths(citationPaths == null || citationPaths.isEmpty()
                ? null : cut(String.join("；", citationPaths), 990));
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);

        ChatConversation c = conversationMapper.selectById(conversationId);
        if (c != null) {
            c.setTurnCount((c.getTurnCount() == null ? 0 : c.getTurnCount()) + 1);
            conversationMapper.updateById(c);
        }
    }

    public List<ChatConversation> myConversations() {
        Long uid = UserContext.require().userId();
        return conversationMapper.selectList(Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getUserId, uid)
                .orderByDesc(ChatConversation::getUpdatedAt)
                .last("limit 30"));
    }

    public List<ChatMessage> messages(Long conversationId) {
        Long uid = UserContext.require().userId();
        ChatConversation c = conversationMapper.selectById(conversationId);
        if (c == null || !c.getUserId().equals(uid)) {
            throw BizException.notFound("会话");
        }
        return messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getId));
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
