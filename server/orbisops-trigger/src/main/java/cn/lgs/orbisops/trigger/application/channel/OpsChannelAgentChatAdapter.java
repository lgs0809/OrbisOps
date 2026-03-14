package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelAgentChatPort;
import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.application.ops.OpsChatApplicationService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.application.security.OpsTrustedRequestMetadata;
import org.springframework.stereotype.Component;

@Component
public final class OpsChannelAgentChatAdapter implements ChannelAgentChatPort {

    private final OpsChatApplicationService chat;
    private final OpsAgentDefinitionQueryGateway agents;

    public OpsChannelAgentChatAdapter(OpsChatApplicationService chat,
                                      OpsAgentDefinitionQueryGateway agents) {
        if (chat == null) throw new IllegalArgumentException("CHANNEL_CHAT_APPLICATION_REQUIRED");
        if (agents == null) throw new IllegalArgumentException("CHANNEL_AGENT_REGISTRY_REQUIRED");
        this.chat = chat;
        this.agents = agents;
    }

    @Override
    public ChatResult chat(ChatCommand command) {
        if (command == null) throw new IllegalArgumentException("CHANNEL_CHAT_COMMAND_REQUIRED");
        OpsAgentDefinition definition = agents.resolveForProject(
                command.definitionId(), command.definitionVersion(), false, command.projectId());
        if (definition == null || definition.getVersion() == null || definition.getVersion() <= 0) {
            throw new IllegalStateException("CHANNEL_EXECUTION_DEFINITION_UNAVAILABLE");
        }
        if (definition.getVersion() != command.definitionVersion()
                || !command.definitionHash().equals(definition.getDefinitionHash())) {
            throw new SecurityException("CHANNEL_EXECUTION_DEFINITION_HASH_MISMATCH");
        }
        String userId = command.identity() == null
                ? "channel:" + command.channelId() + ":" + command.message().senderId()
                : command.identity().platformUserId();
        java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("source", "CHANNEL");
        metadata.put("channelId", command.channelId());
        metadata.put("channelType", command.channelType());
        metadata.put("externalMessageId", command.message().externalMessageId());
        metadata.put("externalConversationId", command.message().externalConversationId());
        metadata.put("executionType", command.executionType().name());
        metadata.put("agentDefinitionHash", command.definitionHash());
        metadata.put("channelIdentityStatus", command.identityStatus().name());
        metadata.put("channelRuntimeAccess", command.runtimeAccess().name());
        if (command.identity() != null) {
            metadata.put("channelIdentityMappingId", command.identity().mappingId());
            metadata.put(OpsTrustedRequestMetadata.AUTH_PRINCIPAL, new AdminAuthService.AuthPrincipal(
                    command.identity().username(), command.identity().platformUserId(),
                    "channel:" + command.identity().mappingId(), AdminAuthService.SCOPE_USER, false));
        }
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId(command.runId())
                .sessionId(command.sessionId())
                .userId(userId)
                .projectId(command.projectId())
                .agentDefinitionId(definition.getAgentId())
                .agentVersion(definition.getVersion())
                .agentDefinition(definition)
                .query(query(command.message()))
                .mode("AGENT")
                .trustedObserveOnly(command.runtimeAccess() == ChannelAgentChatPort.RuntimeAccess.OBSERVE_ONLY_UNKNOWN)
                .metadata(metadata)
                .build();
        OpsAgentChatResponse response = chat.chat(request, userId);
        return new ChatResult(response == null ? "" : response.getContent(), userId);
    }

    private String query(ChannelModels.InboundMessage message) {
        StringBuilder value = new StringBuilder(text(message.text()));
        if (message.action() != null) {
            if (!value.isEmpty()) value.append('\n');
            value.append("[Channel action] type=").append(text(message.action().actionType()))
                    .append(" actionId=").append(text(message.action().actionId()));
            if (!text(message.action().value()).isBlank()) value.append(" value=").append(text(message.action().value()));
        }
        if (!message.attachments().isEmpty()) {
            value.append("\n[Channel attachments]");
            for (ChannelModels.Attachment attachment : message.attachments()) {
                value.append("\n- ").append(attachment.fileName()).append(" (")
                        .append(attachment.mediaType()).append(", contentRef=")
                        .append(attachment.contentRef()).append(", sha256=")
                        .append(attachment.contentHash()).append(')');
            }
        }
        return value.toString();
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
