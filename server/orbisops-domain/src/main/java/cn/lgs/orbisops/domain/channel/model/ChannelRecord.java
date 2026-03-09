package cn.lgs.orbisops.domain.channel.model;

import cn.lgs.orbisops.types.execution.ExecutionBinding;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Channel is a transport endpoint. Inbound execution is an orthogonal binding, not the channel identity itself. */
public record ChannelRecord(
        String channelId,
        String projectId,
        ExecutionBinding inboundExecution,
        String name,
        String channelType,
        String credentialRef,
        Map<String, Object> config,
        ChannelStatus status,
        String createdBy,
        Instant createTime,
        Instant updateTime) {

    public ChannelRecord {
        channelId = required(channelId, "CHANNEL_ID_REQUIRED");
        projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        inboundExecution = inboundExecution == null ? ExecutionBinding.none() : inboundExecution;
        name = required(name, "CHANNEL_NAME_REQUIRED");
        channelType = required(channelType, "CHANNEL_TYPE_REQUIRED").toUpperCase(Locale.ROOT);
        credentialRef = normalize(credentialRef);
        config = config == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(config));
        status = status == null ? ChannelStatus.ACTIVE : status;
        createdBy = required(createdBy, "CHANNEL_ACTOR_REQUIRED");
    }

    public boolean acceptsInbound() {
        return inboundExecution.acceptsInbound();
    }

    private static String required(String value, String reasonCode) {
        String normalized = normalize(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
