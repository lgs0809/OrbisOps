package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.chatsession.model.ChatSessionAgentBindingMode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

/** Creates compatibility Chat Session views from create and chat requests. */
@Component
public final class OpsChatSessionFactory {

    public OpsChatSession create(
            OpsChatSessionCreateRequest request,
            OpsChatSessionAgentBinder.Binding binding) {
        OpsAgentDefinition definition = binding.definition();
        return OpsChatSession.builder()
                .sessionId("chat-session-" + UUID.randomUUID())
                .userId(value(request.getUserId(), "web-user"))
                .projectId(value(request.getProjectId(), ""))
                .agentId(binding.agentId())
                .agentBindingMode(binding.mode().name())
                .agentVersion(definition == null ? null : definition.getVersion())
                .agentDefinitionHash(definition == null
                        ? ""
                        : definition.getDefinitionHash())
                .title(defaultTitle(request.getTitle(), null))
                .mode(value(
                        request.getMode(),
                        StringUtils.hasText(binding.agentId())
                                ? "AGENT"
                                : "MULTI_TURN"))
                .engine(value(
                        request.getEngine(),
                        StringUtils.hasText(binding.agentId())
                                ? "GRAPH"
                                : "CHAT"))
                .ragEnabled(Boolean.TRUE.equals(request.getRagEnabled()))
                .knowledgeBaseId(value(request.getKnowledgeBaseId(), ""))
                .status("ACTIVE")
                .stateVersion(1L)
                .messageCount(0)
                .lastMessage("")
                .metadata(request.getMetadata() == null
                        ? Map.of()
                        : request.getMetadata())
                .build();
    }

    public OpsChatSession restoreMissing(
            OpsAgentChatRequest request,
            OpsChatSessionAgentBinder.Binding binding) {
        OpsAgentDefinition definition = binding.definition();
        return OpsChatSession.builder()
                .sessionId(request.getSessionId())
                .userId(value(request.getUserId(), "web-user"))
                .projectId(value(request.getProjectId(), ""))
                .agentId(binding.agentId())
                .agentBindingMode(binding.mode().name())
                .agentVersion(definition == null ? null : definition.getVersion())
                .agentDefinitionHash(definition == null
                        ? ""
                        : definition.getDefinitionHash())
                .title(defaultTitle(null, request.getQuery()))
                .mode(value(
                        request.getMode(),
                        StringUtils.hasText(binding.agentId())
                                ? "AGENT"
                                : "MULTI_TURN"))
                .engine(value(request.getEngine(), ""))
                .ragEnabled(Boolean.TRUE.equals(request.getRagEnabled()))
                .knowledgeBaseId(value(request.getKnowledgeBaseId(), ""))
                .status("ACTIVE")
                .stateVersion(1L)
                .metadata(request.getMetadata() == null
                        ? Map.of()
                        : request.getMetadata())
                .build();
    }

    public OpsChatSessionCreateRequest createRequest(OpsAgentChatRequest request) {
        String agentId = request.getAgentDefinitionId();
        if (!StringUtils.hasText(agentId)
                && request.getAgentDefinition() != null) {
            agentId = request.getAgentDefinition().getAgentId();
        }
        return OpsChatSessionCreateRequest.builder()
                .userId(value(request.getUserId(), "web-user"))
                .projectId(value(request.getProjectId(), ""))
                .agentId(value(agentId, ""))
                .agentBindingMode(ChatSessionAgentBindingMode.resolve(
                                null,
                                request.getMetadata(),
                                request.getAgentVersion())
                        .name())
                .agentVersion(request.getAgentVersion())
                .title(defaultTitle(null, request.getQuery()))
                .mode(value(
                        request.getMode(),
                        StringUtils.hasText(agentId)
                                ? "AGENT"
                                : "MULTI_TURN"))
                .engine(value(request.getEngine(), ""))
                .ragEnabled(request.getRagEnabled())
                .knowledgeBaseId(request.getKnowledgeBaseId())
                .metadata(request.getMetadata() == null
                        ? Map.of()
                        : request.getMetadata())
                .build();
    }

    public String touchTitle(String currentTitle, String query) {
        return defaultTitle(currentTitle, query);
    }

    public String messageSummary(String lastMessage) {
        return abbreviate(lastMessage, 260);
    }

    private String defaultTitle(String currentTitle, String query) {
        if (StringUtils.hasText(currentTitle)) {
            return currentTitle.trim();
        }
        if (StringUtils.hasText(query)) {
            return abbreviate(query.trim().replaceAll("\\s+", " "), 48);
        }
        return "新会话";
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength) + "...";
    }

    private String value(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
