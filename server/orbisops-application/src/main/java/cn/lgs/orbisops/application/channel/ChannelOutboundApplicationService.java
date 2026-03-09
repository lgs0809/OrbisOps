package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.domain.channel.service.ChannelContentFingerprint;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;

import java.util.LinkedHashMap;
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
        ChannelRecord channel = repository.findById(command.channelId())
                .orElseThrow(() -> new IllegalArgumentException("CHANNEL_NOT_FOUND"));
        if (!command.projectId().equals(channel.projectId())) throw new SecurityException("CHANNEL_PROJECT_MISMATCH");
        if (channel.status() != ChannelStatus.ACTIVE) throw new SecurityException("CHANNEL_DISABLED");
        String target = bounded(command.target(), 256, "CHANNEL_TARGET_TOO_LONG");
        String content = required(contentPolicy.sanitize(command.content()), "CHANNEL_CONTENT_REQUIRED");
        if (content.length() > maxMessageChars) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_TOO_LARGE");
        ChannelOutboundMetadata metadata = ChannelOutboundMetadata.from(command.metadata());
        String messageId = "channel-message-" + UUID.randomUUID();
        String externalMessageId = "outbound-" + UUID.randomUUID();
        String deliveryId = metadata.resolveDeliveryId(messageId);
        ChannelOutboundMetadata deliveryMetadata = metadata.withDeliveryId(deliveryId);
        Map<String, Object> protocolMetadata = deliveryMetadata.toProtocolMap();
        repository.insertOutbound(new ChannelMessageRecord(
                messageId, channel.channelId(), channel.projectId(), externalMessageId, target, "platform",
                deliveryMetadata.sessionId(), deliveryMetadata.runId(), "OUTBOUND", "SENDING",
                payloadEncoder.pending(
                        ChannelContentFingerprint.sha256(content),
                        abbreviate(contentPolicy.sanitize(content), 500),
                        protocolMetadata), null, null, null));
        ChannelDeliveryResult delivery;
        try {
            delivery = deliveryPort.send(channel,
                    ChannelDeliveryConfiguration.from(channel.config()).protocolConfig(),
                    target, content, protocolMetadata);
        } catch (RuntimeException failure) {
            String safeError = contentPolicy.sanitize(text(failure.getMessage()));
            repository.markOutboundFailed(messageId, safeError);
            try {
                auditPort.record(channel.projectId(), "channel:" + channel.channelId(), command.actor(),
                        "CHANNEL_MESSAGE_DELIVERY_FAILED", messageId, "LOW", "FAILED",
                        Map.of("channelId", channel.channelId(), "target", target,
                                "runId", deliveryMetadata.runId(),
                                "deliveryId", deliveryId, "error", safeError));
            } catch (RuntimeException auditFailure) {
                failure.addSuppressed(auditFailure);
            }
            throw new IllegalStateException("CHANNEL_DELIVERY_FAILED:" + safeError, failure);
        }
        ChannelDeliveryResult safeDelivery = delivery == null
                ? new ChannelDeliveryResult(false, Map.of())
                : delivery;
        boolean delivered = safeDelivery.delivered();
        repository.markOutboundCompleted(messageId, delivered ? "DELIVERED" : "NOT_CONFIGURED",
                payloadEncoder.delivery(safeDelivery.payload()));
        try {
            auditPort.record(channel.projectId(), "channel:" + channel.channelId(), command.actor(),
                    delivered ? "CHANNEL_MESSAGE_DELIVERED" : "CHANNEL_DELIVERY_NOT_CONFIGURED",
                    messageId, "LOW", delivered ? "SUCCEEDED" : "SKIPPED",
                    Map.of("channelId", channel.channelId(), "target", target,
                            "runId", deliveryMetadata.runId(),
                            "deliveryId", deliveryId));
        } catch (RuntimeException auditFailure) {
            throw new IllegalStateException("CHANNEL_DELIVERY_AUDIT_FAILED_AFTER_SEND", auditFailure);
        }
        Map<String, Object> result = new LinkedHashMap<>(safeDelivery.payload());
        result.put("messageId", messageId);
        result.put("channelId", channel.channelId());
        result.put("deliveryId", deliveryId);
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
