package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.domain.channel.service.ChannelContentFingerprint;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChannelOutboundApplicationService {

    private final IChannelRepository repository;
    private final ChannelOutboundDeliveryPort deliveryPort;
    private final ChannelRuntimeAuditPort auditPort;
    private final ChannelOutboundContentPolicy contentPolicy;
    private final ChannelOutboundPayloadEncoder payloadEncoder = new ChannelOutboundPayloadEncoder();
    private final int maxMessageChars;

    public ChannelOutboundApplicationService(IChannelRepository repository,
                                             ChannelOutboundDeliveryPort deliveryPort,
                                             ChannelRuntimeAuditPort auditPort,
                                             ChannelOutboundContentPolicy contentPolicy,
                                             int maxMessageChars) {
        if (repository == null) throw new IllegalArgumentException("CHANNEL_REPOSITORY_REQUIRED");
        if (deliveryPort == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_DELIVERY_PORT_REQUIRED");
        if (auditPort == null) throw new IllegalArgumentException("CHANNEL_RUNTIME_AUDIT_PORT_REQUIRED");
        if (contentPolicy == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_CONTENT_POLICY_REQUIRED");
        this.repository = repository;
        this.deliveryPort = deliveryPort;
        this.auditPort = auditPort;
        this.contentPolicy = contentPolicy;
        this.maxMessageChars = Math.max(500, maxMessageChars);
    }

    public Map<String, Object> send(ChannelModels.Send command) {
        if (command == null) throw new IllegalArgumentException("CHANNEL_SEND_COMMAND_REQUIRED");
        String content = required(contentPolicy.sanitize(command.content()), "CHANNEL_CONTENT_REQUIRED");
        ChannelOutboundMetadata metadata = ChannelOutboundMetadata.from(command.metadata());
        return sendInternal(command.projectId(), command.channelId(), command.target(), ChannelRichContent.text(content),
                metadata, command.actor());
    }

    public Map<String, Object> sendRich(ChannelModels.RichSend command) {
        if (command == null) throw new IllegalArgumentException("CHANNEL_RICH_SEND_COMMAND_REQUIRED");
        ChannelRichContent source = command.content();
        ChannelRichContent sanitized = new ChannelRichContent(
                contentPolicy.sanitize(source.plainText()),
                contentPolicy.sanitize(source.markdown()),
                source.actions());
        ChannelOutboundMetadata metadata = ChannelOutboundMetadata.from(command.metadata());
        return sendInternal(command.projectId(), command.channelId(), command.target(), sanitized,
                metadata, command.actor());
    }

    public Map<String, Object> update(ChannelModels.Update command) {
        if (command == null) throw new IllegalArgumentException("CHANNEL_UPDATE_COMMAND_REQUIRED");
        String content = required(contentPolicy.sanitize(command.content()), "CHANNEL_CONTENT_REQUIRED");
        if (content.length() > maxMessageChars) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_TOO_LARGE");
        ChannelRecord channel = repository.findById(command.channelId())
                .orElseThrow(() -> new IllegalArgumentException("CHANNEL_NOT_FOUND"));
        if (!command.projectId().equals(channel.projectId())) throw new SecurityException("CHANNEL_PROJECT_MISMATCH");
        if (channel.status() != ChannelStatus.ACTIVE) throw new SecurityException("CHANNEL_DISABLED");
        if (!deliveryPort.supportsMessageUpdate(channel)) {
            throw new UnsupportedOperationException("CHANNEL_MESSAGE_UPDATE_UNSUPPORTED:" + channel.channelType());
        }
        String target = bounded(command.target(), 256, "CHANNEL_TARGET_TOO_LONG");
        ChannelOutboundMetadata metadata = ChannelOutboundMetadata.from(command.metadata())
                .withDeliveryId(command.messageId());
        ChannelOutboundMessage outboundMessage = new ChannelOutboundMessage(
                new ChannelConversationRef(target, ChannelConversationRef.ConversationKind.UNKNOWN),
                ChannelRichContent.text(content),
                List.of(),
                metadata);
        ChannelDeliveryReceipt delivery;
        try {
            delivery = deliveryPort.update(
                    channel,
                    new ChannelMessageRef(
                            command.externalMessageId(),
                            new ChannelConversationRef(target, ChannelConversationRef.ConversationKind.UNKNOWN)),
                    outboundMessage);
        } catch (RuntimeException failure) {
            String safeError = contentPolicy.sanitize(text(failure.getMessage()));
            repository.markOutboundFailed(command.messageId(), safeError);
            try {
                auditPort.record(channel.projectId(), "channel:" + channel.channelId(), command.actor(),
                        "CHANNEL_MESSAGE_UPDATE_FAILED", command.messageId(), "LOW", "FAILED",
                        Map.of("channelId", channel.channelId(), "target", target,
                                "runId", metadata.runId(), "error", safeError));
            } catch (RuntimeException auditFailure) {
                failure.addSuppressed(auditFailure);
            }
            throw new IllegalStateException("CHANNEL_UPDATE_FAILED:" + safeError, failure);
        }
        ChannelDeliveryReceipt safeDelivery = delivery == null
                ? ChannelDeliveryReceipt.skipped("CHANNEL_UPDATE_RESULT_MISSING")
                : delivery;
        Map<String, Object> deliveryView = deliveryView(safeDelivery);
        repository.markOutboundCompleted(command.messageId(), safeDelivery.status(), payloadEncoder.delivery(deliveryView));
        auditPort.record(channel.projectId(), "channel:" + channel.channelId(), command.actor(),
                "CHANNEL_MESSAGE_UPDATED", command.messageId(), "LOW",
                safeDelivery.delivered() ? "SUCCEEDED" : "SKIPPED",
                Map.of("channelId", channel.channelId(), "target", target,
                        "runId", metadata.runId(), "providerStatus", safeDelivery.status()));
        Map<String, Object> result = new LinkedHashMap<>(deliveryView);
        result.put("messageId", command.messageId());
        result.put("channelId", channel.channelId());
        result.put("updated", safeDelivery.delivered());
        return Map.copyOf(result);
    }

    private Map<String, Object> sendInternal(String projectId,
                                             String channelId,
                                             String rawTarget,
                                             ChannelRichContent content,
                                             ChannelOutboundMetadata metadata,
                                             String actor) {
        ChannelRecord channel = repository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("CHANNEL_NOT_FOUND"));
        if (!projectId.equals(channel.projectId())) throw new SecurityException("CHANNEL_PROJECT_MISMATCH");
        if (channel.status() != ChannelStatus.ACTIVE) throw new SecurityException("CHANNEL_DISABLED");
        String target = bounded(rawTarget, 256, "CHANNEL_TARGET_TOO_LONG");
        String summary = contentSummary(content);
        if (summary.length() > maxMessageChars) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_TOO_LARGE");
        String messageId = "channel-message-" + UUID.randomUUID();
        String externalMessageId = "outbound-" + UUID.randomUUID();
        String deliveryId = metadata.resolveDeliveryId(messageId);
        ChannelOutboundMetadata deliveryMetadata = metadata.withDeliveryId(deliveryId);
        Map<String, Object> protocolMetadata = deliveryMetadata.toProtocolMap();
        repository.insertOutbound(new ChannelMessageRecord(
                messageId, channel.channelId(), channel.projectId(), externalMessageId, target,
                progressSender(deliveryMetadata),
                deliveryMetadata.sessionId(), deliveryMetadata.runId(), "OUTBOUND", "SENDING",
                payloadEncoder.pending(
                        ChannelContentFingerprint.sha256(summary),
                        abbreviate(contentPolicy.sanitize(summary), 500),
                        protocolMetadata), null, null, null));
        ChannelOutboundMessage outboundMessage = new ChannelOutboundMessage(
                new ChannelConversationRef(target, ChannelConversationRef.ConversationKind.UNKNOWN),
                content,
                List.of(),
                deliveryMetadata);
        ChannelDeliveryReceipt delivery;
        try {
            delivery = deliveryPort.send(channel, outboundMessage);
        } catch (RuntimeException failure) {
            String safeError = contentPolicy.sanitize(text(failure.getMessage()));
            repository.markOutboundFailed(messageId, safeError);
            try {
                auditPort.record(channel.projectId(), "channel:" + channel.channelId(), actor,
                        "CHANNEL_MESSAGE_DELIVERY_FAILED", messageId, "LOW", "FAILED",
                        Map.of("channelId", channel.channelId(), "target", target,
                                "runId", deliveryMetadata.runId(),
                                "deliveryId", deliveryId, "error", safeError));
            } catch (RuntimeException auditFailure) {
                failure.addSuppressed(auditFailure);
            }
            throw new IllegalStateException("CHANNEL_DELIVERY_FAILED:" + safeError, failure);
        }
        ChannelDeliveryReceipt safeDelivery = delivery == null
                ? ChannelDeliveryReceipt.skipped("CHANNEL_DELIVERY_RESULT_MISSING")
                : delivery;
        Map<String, Object> deliveryView = deliveryView(safeDelivery);
        boolean delivered = safeDelivery.delivered();
        if (!safeDelivery.providerMessageId().isBlank()) {
            repository.bindOutboundExternalMessageId(messageId, safeDelivery.providerMessageId());
        }
        repository.markOutboundCompleted(messageId, delivered ? "DELIVERED" : safeDelivery.status(),
                payloadEncoder.delivery(deliveryView));
        try {
            auditPort.record(channel.projectId(), "channel:" + channel.channelId(), actor,
                    delivered ? "CHANNEL_MESSAGE_DELIVERED" : "CHANNEL_DELIVERY_NOT_CONFIGURED",
                    messageId, "LOW", delivered ? "SUCCEEDED" : "SKIPPED",
                    Map.of("channelId", channel.channelId(), "target", target,
                            "runId", deliveryMetadata.runId(),
                            "deliveryId", deliveryId,
                            "providerStatus", safeDelivery.status()));
        } catch (RuntimeException auditFailure) {
            throw new IllegalStateException("CHANNEL_DELIVERY_AUDIT_FAILED_AFTER_SEND", auditFailure);
        }
        Map<String, Object> result = new LinkedHashMap<>(deliveryView);
        result.put("messageId", messageId);
        result.put("channelId", channel.channelId());
        result.put("deliveryId", deliveryId);
        return Map.copyOf(result);
    }

    private String contentSummary(ChannelRichContent content) {
        String plain = text(content == null ? null : content.plainText());
        String markdown = text(content == null ? null : content.markdown());
        String summary = plain.isBlank() ? markdown : plain;
        if (summary.isBlank() && content != null && !content.actions().isEmpty()) {
            summary = content.actions().stream().map(action -> text(action.label())).filter(value -> !value.isBlank())
                    .reduce((left, right) -> left + " | " + right).orElse("");
        }
        return required(summary, "CHANNEL_CONTENT_REQUIRED");
    }

    private String progressSender(ChannelOutboundMetadata metadata) {
        return metadata != null && ChannelRunProgressContract.SOURCE.equalsIgnoreCase(metadata.source())
                ? ChannelRunProgressContract.SENDER
                : "platform";
    }

    private Map<String, Object> deliveryView(ChannelDeliveryReceipt receipt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("delivered", receipt.delivered());
        result.put("status", receipt.status());
        if (!receipt.providerMessageId().isBlank()) {
            result.put("providerMessageId", receipt.providerMessageId());
            result.put("externalMessageId", receipt.providerMessageId());
        }
        if (receipt.providerStatusCode() != null) result.put("providerStatusCode", receipt.providerStatusCode());
        result.put("observedAt", receipt.observedAt().toString());
        return Map.copyOf(result);
    }

    private String bounded(String value, int max, String reasonCode) {
        String normalized = required(value, "CHANNEL_TARGET_REQUIRED");
        if (normalized.length() > max) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String abbreviate(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, max) + "...";
    }
}
