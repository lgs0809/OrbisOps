package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.types.execution.ExecutionType;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChannelModels {

    private ChannelModels() {
    }

    public record Attachment(String attachmentId,
                             String fileName,
                             String mediaType,
                             long sizeBytes,
                             String contentRef,
                             String contentHash) {
        public Attachment {
            if (sizeBytes < 0) throw new IllegalArgumentException("CHANNEL_ATTACHMENT_SIZE_INVALID");
            attachmentId = normalizeText(attachmentId);
            fileName = normalizeText(fileName);
            mediaType = normalizeText(mediaType);
            contentRef = normalizeText(contentRef);
            contentHash = normalizeText(contentHash);
        }
    }

    public record Action(String actionId,
                         String actionType,
                         String value,
                         Map<String, Object> parameters) {
        public Action {
            actionId = normalizeText(actionId);
            actionType = normalizeText(actionType).toUpperCase(Locale.ROOT);
            value = normalizeText(value);
            parameters = copy(parameters);
        }
    }

    public record InboundMessage(String externalMessageId,
                                 String externalConversationId,
                                 String senderId,
                                 String text,
                                 long timestamp,
                                 Map<String, Object> metadata,
                                 String messageType,
                                 List<Attachment> attachments,
                                 Action action) {
        public InboundMessage {
            externalMessageId = required(externalMessageId, "CHANNEL_EXTERNAL_MESSAGE_ID_REQUIRED");
            externalConversationId = required(externalConversationId, "CHANNEL_CONVERSATION_ID_REQUIRED");
            senderId = required(senderId, "CHANNEL_SENDER_ID_REQUIRED");
            text = normalizeText(text);
            if (timestamp <= 0) throw new IllegalArgumentException("CHANNEL_MESSAGE_TIMESTAMP_INVALID");
            metadata = copy(metadata);
            messageType = normalizeText(messageType).isBlank()
                    ? "TEXT"
                    : normalizeText(messageType).toUpperCase(Locale.ROOT);
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    public record Receive(String channelId,
                          InboundMessage message,
                          String timestampHeader,
                          String signature) {
        public Receive {
            channelId = required(channelId, "CHANNEL_ID_REQUIRED");
            if (message == null) throw new IllegalArgumentException("CHANNEL_MESSAGE_REQUIRED");
            timestampHeader = required(timestampHeader, "CHANNEL_SIGNATURE_TIMESTAMP_REQUIRED");
            signature = required(signature, "CHANNEL_SIGNATURE_REQUIRED");
        }
    }

    public record Send(String projectId,
                       String channelId,
                       String target,
                       String content,
                       Map<String, Object> metadata,
                       String actor) {
        public Send {
            projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
            channelId = required(channelId, "CHANNEL_ID_REQUIRED");
            target = required(target, "CHANNEL_TARGET_REQUIRED");
            content = required(content, "CHANNEL_CONTENT_REQUIRED");
            metadata = copy(metadata);
            actor = required(actor, "CHANNEL_ACTOR_REQUIRED");
        }
    }

    public record RichSend(String projectId,
                           String channelId,
                           String target,
                           ChannelRichContent content,
                           Map<String, Object> metadata,
                           String actor) {
        public RichSend {
            projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
            channelId = required(channelId, "CHANNEL_ID_REQUIRED");
            target = required(target, "CHANNEL_TARGET_REQUIRED");
            if (content == null) throw new IllegalArgumentException("CHANNEL_CONTENT_REQUIRED");
            metadata = copy(metadata);
            actor = required(actor, "CHANNEL_ACTOR_REQUIRED");
        }
    }

    public record Update(String projectId,
                         String channelId,
                         String target,
                         String messageId,
                         String externalMessageId,
                         String content,
                         Map<String, Object> metadata,
                         String actor) {
        public Update {
            projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
            channelId = required(channelId, "CHANNEL_ID_REQUIRED");
            target = required(target, "CHANNEL_TARGET_REQUIRED");
            messageId = required(messageId, "CHANNEL_MESSAGE_ID_REQUIRED");
            externalMessageId = required(externalMessageId, "CHANNEL_EXTERNAL_MESSAGE_ID_REQUIRED");
            content = required(content, "CHANNEL_CONTENT_REQUIRED");
            metadata = copy(metadata);
            actor = required(actor, "CHANNEL_ACTOR_REQUIRED");
        }
    }

    public record Recovery(String projectId,
                           String channelId,
                           String externalMessageId,
                           boolean confirmedNoSideEffect,
                           String actor) {
        public Recovery {
            projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
            channelId = required(channelId, "CHANNEL_ID_REQUIRED");
            externalMessageId = required(externalMessageId, "CHANNEL_EXTERNAL_MESSAGE_ID_REQUIRED");
            actor = required(actor, "CHANNEL_ACTOR_REQUIRED");
        }
    }

    public record Field<T>(boolean supplied, T value) {
        public static <T> Field<T> absent() {
            return new Field<>(false, null);
        }

        public static <T> Field<T> supplied(T value) {
            return new Field<>(true, value);
        }

        public T orElse(T fallback) {
            return supplied ? value : fallback;
        }
    }

    public record ConfigurationMutation(
            String projectId,
            String channelId,
            Field<String> requestedProjectId,
            Field<String> requestedChannelId,
            Field<ExecutionType> executionType,
            Field<String> workflowId,
            Field<ExecutionVersionPolicy> workflowVersionPolicy,
            Field<Integer> workflowVersion,
            Field<String> name,
            Field<String> channelType,
            Field<String> credentialRef,
            Field<Map<String, Object>> configuration,
            Field<ChannelAccessPolicy> accessPolicy,
            Field<ChannelStatus> status,
            String actor) {
        public ConfigurationMutation {
            projectId = normalizeText(projectId);
            channelId = normalizeText(channelId);
            requestedProjectId = field(requestedProjectId);
            requestedChannelId = field(requestedChannelId);
            executionType = field(executionType);
            workflowId = field(workflowId);
            workflowVersionPolicy = field(workflowVersionPolicy);
            workflowVersion = field(workflowVersion);
            name = field(name);
            channelType = field(channelType);
            credentialRef = field(credentialRef);
            configuration = configuration == null || !configuration.supplied()
                    ? Field.absent()
                    : Field.supplied(copy(configuration.value()));
            accessPolicy = field(accessPolicy);
            status = field(status);
            actor = required(actor, "CHANNEL_ACTOR_REQUIRED");
        }
    }

    public record IdentityBinding(
            String projectId,
            String channelId,
            String externalSenderId,
            String platformUserId,
            String username,
            ChannelStatus status,
            long expectedVersion,
            String actor) {
        public IdentityBinding {
            projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
            channelId = required(channelId, "CHANNEL_ID_REQUIRED");
            externalSenderId = required(externalSenderId, "CHANNEL_EXTERNAL_IDENTITY_REQUIRED");
            platformUserId = normalizeText(platformUserId);
            username = normalizeText(username);
            status = status == null ? ChannelStatus.ACTIVE : status;
            actor = required(actor, "CHANNEL_ACTOR_REQUIRED");
        }
    }

    private static <T> Field<T> field(Field<T> value) {
        return value == null ? Field.absent() : value;
    }

    private static String required(String value, String reasonCode) {
        String normalized = normalizeText(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, Object> copy(Map<String, Object> value) {
        if (value == null || value.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
