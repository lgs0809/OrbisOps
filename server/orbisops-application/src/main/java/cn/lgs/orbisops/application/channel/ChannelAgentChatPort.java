package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.types.execution.ExecutionType;

import java.util.Locale;

public interface ChannelAgentChatPort {

    ChatResult chat(ChatCommand command);

    record ChatCommand(String projectId,
                       String channelId,
                       String channelType,
                       String runId,
                       String sessionId,
                       ExecutionType executionType,
                       String definitionId,
                       int definitionVersion,
                       String definitionHash,
                       ChannelModels.InboundMessage message,
                       ChannelIdentityRecord identity,
                       IdentityStatus identityStatus,
                       RuntimeAccess runtimeAccess) {
        public ChatCommand {
            projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
            channelId = required(channelId, "CHANNEL_ID_REQUIRED");
            channelType = required(channelType, "CHANNEL_TYPE_REQUIRED").toUpperCase(Locale.ROOT);
            runId = required(runId, "CHANNEL_RUN_ID_REQUIRED");
            sessionId = required(sessionId, "CHANNEL_SESSION_ID_REQUIRED");
            if (executionType == null || executionType == ExecutionType.NONE) {
                throw new IllegalArgumentException("CHANNEL_CHAT_EXECUTION_REQUIRED");
            }
            definitionId = required(definitionId, "CHANNEL_EXECUTION_DEFINITION_REQUIRED");
            definitionHash = required(definitionHash, "CHANNEL_EXECUTION_DEFINITION_HASH_REQUIRED");
            if (definitionVersion <= 0) throw new IllegalArgumentException("CHANNEL_EXECUTION_VERSION_REQUIRED");
            if (message == null) throw new IllegalArgumentException("CHANNEL_MESSAGE_REQUIRED");
            identityStatus = identityStatus == null
                    ? identity == null ? IdentityStatus.UNMAPPED : IdentityStatus.MAPPED
                    : identityStatus;
            if (identityStatus == IdentityStatus.MAPPED && identity == null) {
                throw new IllegalArgumentException("CHANNEL_MAPPED_IDENTITY_REQUIRED");
            }
            if (identityStatus != IdentityStatus.MAPPED && identity != null) {
                throw new IllegalArgumentException("CHANNEL_UNTRUSTED_IDENTITY_MUST_NOT_BE_PROPAGATED");
            }
            runtimeAccess = runtimeAccess == null ? RuntimeAccess.AUTHENTICATED : runtimeAccess;
            if (identityStatus == IdentityStatus.INVALID) {
                throw new SecurityException("CHANNEL_INVALID_IDENTITY_MUST_NOT_ENTER_RUNTIME");
            }
            if (runtimeAccess == RuntimeAccess.AUTHENTICATED && identityStatus != IdentityStatus.MAPPED) {
                throw new SecurityException("CHANNEL_AUTHENTICATED_RUNTIME_REQUIRES_MAPPED_IDENTITY");
            }
            if (runtimeAccess == RuntimeAccess.OBSERVE_ONLY_UNKNOWN && identityStatus != IdentityStatus.UNMAPPED) {
                throw new SecurityException("CHANNEL_OBSERVE_ONLY_REQUIRES_UNKNOWN_IDENTITY");
            }
        }
    }

    enum IdentityStatus {
        MAPPED,
        INVALID,
        UNMAPPED
    }

    enum RuntimeAccess {
        AUTHENTICATED,
        OBSERVE_ONLY_UNKNOWN
    }

    record ChatResult(String content, String userId) {
        public ChatResult {
            content = required(content, "CHANNEL_CHAT_RESPONSE_REQUIRED");
            userId = required(userId, "CHANNEL_CHAT_USER_REQUIRED");
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
