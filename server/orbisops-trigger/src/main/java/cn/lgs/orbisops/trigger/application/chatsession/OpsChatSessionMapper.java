package cn.lgs.orbisops.trigger.application.chatsession;

import cn.lgs.orbisops.domain.chatsession.model.ChatMessageSnapshot;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSnapshot;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatMessageView;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSession;

import java.util.Map;

/** Anti-corruption mapper between Trigger views and Chat Session Domain snapshots. */
public class OpsChatSessionMapper {

    public ChatSessionSnapshot snapshot(OpsChatSession session) {
        if (session == null) return null;
        return new ChatSessionSnapshot(
                session.getSessionId(),
                session.getUserId(),
                value(session.getProjectId(), ""),
                value(session.getAgentId(), ""),
                value(session.getAgentBindingMode(), "LATEST_PUBLISHED"),
                session.getAgentVersion(),
                value(session.getAgentDefinitionHash(), ""),
                value(session.getTitle(), "新会话"),
                value(session.getMode(), "MULTI_TURN"),
                value(session.getEngine(), "CHAT"),
                Boolean.TRUE.equals(session.getRagEnabled()),
                value(session.getKnowledgeBaseId(), ""),
                value(session.getStatus(), "ACTIVE"),
                session.getStateVersion() == null ? 1L : session.getStateVersion(),
                session.getMetadata() == null ? Map.of() : session.getMetadata(),
                session.getCreatedAt(),
                session.getLastActiveAt(),
                session.getMessageCount() == null ? 0 : session.getMessageCount(),
                value(session.getLastMessage(), ""));
    }

    public OpsChatSession view(ChatSessionSnapshot session) {
        if (session == null) return null;
        return OpsChatSession.builder()
                .sessionId(session.sessionId())
                .userId(session.userId())
                .projectId(session.projectId())
                .agentId(session.agentId())
                .agentBindingMode(session.agentBindingMode())
                .agentVersion(session.agentVersion())
                .agentDefinitionHash(session.agentDefinitionHash())
                .title(session.title())
                .mode(session.mode())
                .engine(session.engine())
                .ragEnabled(session.ragEnabled())
                .knowledgeBaseId(session.knowledgeBaseId())
                .status(session.status())
                .stateVersion(session.stateVersion())
                .createdAt(session.createdAt())
                .lastActiveAt(session.lastActiveAt())
                .messageCount(session.messageCount())
                .lastMessage(abbreviate(session.lastMessage(), 260))
                .metadata(session.metadata())
                .build();
    }

    public OpsChatMessageView view(ChatMessageSnapshot message) {
        if (message == null) return null;
        return OpsChatMessageView.builder()
                .messageId(message.messageId())
                .sessionId(message.sessionId())
                .userId(message.userId())
                .role(message.role())
                .content(message.content())
                .createdAt(message.createdAt())
                .metadata(message.metadata())
                .build();
    }

    private String abbreviate(String input, int maxLength) {
        if (!hasText(input)) return "";
        return input.length() <= maxLength ? input : input.substring(0, maxLength) + "...";
    }

    private String value(String input, String fallback) {
        return hasText(input) ? input : fallback;
    }

    private boolean hasText(String input) {
        return input != null && !input.trim().isBlank();
    }
}
