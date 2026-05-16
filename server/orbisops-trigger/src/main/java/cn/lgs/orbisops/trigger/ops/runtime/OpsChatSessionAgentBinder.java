package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.chatsession.model.ChatSessionAgentBindingMode;
import cn.lgs.orbisops.domain.chatsession.service.ChatSessionAgentBindingPolicy;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;

/** Resolves and applies Chat Session Agent version bindings. */
@Component
public final class OpsChatSessionAgentBinder {

    private final OpsAgentDefinitionQueryGateway definitionRegistry;
    private final ChatSessionAgentBindingPolicy bindingPolicy;

    public OpsChatSessionAgentBinder(
            OpsAgentDefinitionQueryGateway definitionRegistry) {
        if (definitionRegistry == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_QUERY_GATEWAY_REQUIRED");
        }
        this.definitionRegistry = definitionRegistry;
        this.bindingPolicy = new ChatSessionAgentBindingPolicy();
    }

    public Binding resolve(OpsChatSessionCreateRequest request) {
        String projectId = value(request.getProjectId());
        String agentId = value(request.getAgentId());
        ChatSessionAgentBindingMode mode = ChatSessionAgentBindingMode.resolve(
                request.getAgentBindingMode(),
                request.getMetadata(),
                request.getAgentVersion());
        return new Binding(
                agentId,
                mode,
                resolveAgent(projectId, agentId, mode, request.getAgentVersion()));
    }

    public Binding resolve(OpsAgentChatRequest request) {
        String agentId = agentId(request);
        ChatSessionAgentBindingMode mode = ChatSessionAgentBindingMode.resolve(
                null,
                request.getMetadata(),
                request.getAgentVersion());
        return new Binding(
                agentId,
                mode,
                resolveAgent(
                        value(request.getProjectId()),
                        agentId,
                        mode,
                        request.getAgentVersion()));
    }

    public void bindRequestToSession(
            OpsAgentChatRequest request,
            OpsChatSession session) {
        if (request == null || session == null) {
            return;
        }
        if (!StringUtils.hasText(request.getProjectId())
                && StringUtils.hasText(session.getProjectId())) {
            request.setProjectId(session.getProjectId());
        }
        if (!StringUtils.hasText(session.getAgentId())) {
            return;
        }
        ChatSessionAgentBindingMode mode = ChatSessionAgentBindingMode.resolve(
                session.getAgentBindingMode(),
                session.getMetadata(),
                session.getAgentVersion());
        OpsAgentDefinition resolved = resolveAgent(
                session.getProjectId(),
                session.getAgentId(),
                mode,
                session.getAgentVersion());
        bindingPolicy.verifyPinnedHash(
                mode,
                session.getAgentDefinitionHash(),
                resolved.getDefinitionHash());
        request.setAgentDefinitionId(resolved.getAgentId());
        request.setAgentVersion(resolved.getVersion());
        request.setAgentDefinition(resolved);
        if (request.getMetadata() == null) {
            request.setMetadata(new LinkedHashMap<>());
        }
        request.getMetadata().put("agentBindingMode", mode.name());
        request.getMetadata().put(
                "agentDefinitionHash", resolved.getDefinitionHash());
    }

    public String agentId(OpsAgentChatRequest request) {
        if (request == null) {
            return "";
        }
        if (StringUtils.hasText(request.getAgentDefinitionId())) {
            return request.getAgentDefinitionId();
        }
        return request.getAgentDefinition() == null
                ? ""
                : value(request.getAgentDefinition().getAgentId());
    }

    private OpsAgentDefinition resolveAgent(
            String projectId,
            String agentId,
            ChatSessionAgentBindingMode bindingMode,
            Integer requestedVersion) {
        if (!StringUtils.hasText(agentId)) {
            return null;
        }
        Integer version = bindingMode.selectedVersion(requestedVersion);
        OpsAgentDefinition resolved = StringUtils.hasText(projectId)
                ? definitionRegistry.resolveForProject(
                agentId, version, false, projectId)
                : definitionRegistry.resolve(agentId, version, false);
        bindingPolicy.requireResolved(
                resolved == null ? "" : resolved.getAgentId(),
                resolved == null ? null : resolved.getVersion(),
                resolved == null ? "" : resolved.getDefinitionHash());
        return resolved;
    }

    private String value(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

    public record Binding(
            String agentId,
            ChatSessionAgentBindingMode mode,
            OpsAgentDefinition definition) {
    }
}
