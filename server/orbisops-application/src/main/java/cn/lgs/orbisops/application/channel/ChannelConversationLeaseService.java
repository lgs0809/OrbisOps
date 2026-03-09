package cn.lgs.orbisops.application.channel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChannelConversationLeaseService {

    private final ChannelConversationLeasePort port;
    private final Clock clock;
    private final Map<String, LeaseHandle> activeLeases = new ConcurrentHashMap<>();

    public ChannelConversationLeaseService(ChannelConversationLeasePort port) {
        this(port, Clock.systemUTC());
    }

    ChannelConversationLeaseService(ChannelConversationLeasePort port, Clock clock) {
        if (port == null) throw new IllegalArgumentException("CHANNEL_CONVERSATION_LEASE_PORT_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("CHANNEL_CONVERSATION_LEASE_CLOCK_REQUIRED");
        this.port = port;
        this.clock = clock;
    }

    public Optional<LeaseHandle> acquire(AcquireRequest request, Duration leaseDuration) {
        if (request == null) throw new IllegalArgumentException("CHANNEL_LEASE_ACQUIRE_REQUEST_REQUIRED");
        Duration duration = duration(leaseDuration);
        String lockToken = UUID.randomUUID().toString();
        Instant expiresAt = clock.instant().plus(duration);
        if (!port.tryAcquireConversation(request.channelId(), request.externalConversationId(), request.senderId(),
                lockToken, expiresAt)) {
            return Optional.empty();
        }
        boolean acquiredInbound = false;
        try {
            if (!port.isOldestUnfinishedInbound(request.channelId(), request.externalConversationId(),
                    request.senderId(), request.externalMessageId())) {
                return Optional.empty();
            }
            if (!port.tryAcquireInbound(request.channelId(), request.externalMessageId(), lockToken, expiresAt)) {
                return Optional.empty();
            }
            acquiredInbound = true;
            LeaseHandle handle = new LeaseHandle(request.projectId(), request.channelId(),
                    request.externalConversationId(), request.senderId(), request.externalMessageId(),
                    lockToken, expiresAt);
            activeLeases.put(lockToken, handle);
            return Optional.of(handle);
        } finally {
            if (!acquiredInbound) {
                port.releaseConversation(request.channelId(), request.externalConversationId(), request.senderId(), lockToken);
            }
        }
    }

    public List<LeaseLost> renewActive(Duration leaseDuration) {
        Instant nextExpiry = clock.instant().plus(duration(leaseDuration));
        return activeLeases.entrySet().stream().map(entry -> {
            LeaseHandle current = entry.getValue();
            boolean renewed = port.renew(current.channelId(), current.externalConversationId(), current.senderId(),
                    current.externalMessageId(), current.lockToken(), nextExpiry);
            if (renewed) {
                activeLeases.replace(entry.getKey(), current, current.withExpiresAt(nextExpiry));
                return null;
            }
            activeLeases.remove(entry.getKey(), current);
            port.releaseConversation(current.channelId(), current.externalConversationId(), current.senderId(),
                    current.lockToken());
            return new LeaseLost(current.projectId(), current.channelId(), current.externalMessageId(),
                    current.lockToken());
        }).filter(java.util.Objects::nonNull).toList();
    }

    public void release(LeaseHandle handle) {
        if (handle == null) return;
        LeaseHandle active = activeLeases.remove(handle.lockToken());
        if (active != null) {
            port.releaseConversation(active.channelId(), active.externalConversationId(), active.senderId(),
                    active.lockToken());
        }
    }

    public boolean complete(LeaseHandle handle, String status, String runId, String sessionId) {
        if (!isActive(handle)) return false;
        return port.complete(handle.channelId(), handle.externalMessageId(), handle.lockToken(),
                required(status, "CHANNEL_COMPLETION_STATUS_REQUIRED"), text(runId), text(sessionId));
    }

    public boolean fail(LeaseHandle handle, String errorMessage) {
        if (!isActive(handle)) return false;
        return port.fail(handle.channelId(), handle.externalMessageId(), handle.lockToken(), text(errorMessage));
    }

    public List<ChannelConversationLeasePort.RecoveredLease> recoverExpired(int limit) {
        if (limit <= 0 || limit > 1000) throw new IllegalArgumentException("CHANNEL_LEASE_RECOVERY_LIMIT_INVALID");
        return port.recoverExpired(limit);
    }

    public int activeLeaseCount() {
        return activeLeases.size();
    }

    private boolean isActive(LeaseHandle handle) {
        return handle != null && activeLeases.containsKey(handle.lockToken());
    }

    private Duration duration(Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("CHANNEL_LEASE_DURATION_INVALID");
        }
        return value.compareTo(Duration.ofSeconds(30)) < 0 ? Duration.ofSeconds(30) : value;
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record AcquireRequest(String projectId,
                                 String channelId,
                                 String externalConversationId,
                                 String senderId,
                                 String externalMessageId) {
        public AcquireRequest {
            projectId = text(projectId);
            channelId = required(channelId, "CHANNEL_ID_REQUIRED");
            externalConversationId = required(externalConversationId, "CHANNEL_CONVERSATION_ID_REQUIRED");
            senderId = required(senderId, "CHANNEL_SENDER_ID_REQUIRED");
            externalMessageId = required(externalMessageId, "CHANNEL_EXTERNAL_MESSAGE_ID_REQUIRED");
        }
    }

    public record LeaseHandle(String projectId,
                              String channelId,
                              String externalConversationId,
                              String senderId,
                              String externalMessageId,
                              String lockToken,
                              Instant expiresAt) {
        public LeaseHandle {
            projectId = text(projectId);
            channelId = required(channelId, "CHANNEL_ID_REQUIRED");
            externalConversationId = required(externalConversationId, "CHANNEL_CONVERSATION_ID_REQUIRED");
            senderId = required(senderId, "CHANNEL_SENDER_ID_REQUIRED");
            externalMessageId = required(externalMessageId, "CHANNEL_EXTERNAL_MESSAGE_ID_REQUIRED");
            lockToken = required(lockToken, "CHANNEL_LEASE_TOKEN_REQUIRED");
            if (expiresAt == null) throw new IllegalArgumentException("CHANNEL_LEASE_EXPIRY_REQUIRED");
        }

        LeaseHandle withExpiresAt(Instant value) {
            return new LeaseHandle(projectId, channelId, externalConversationId, senderId, externalMessageId,
                    lockToken, value);
        }
    }

    public record LeaseLost(String projectId,
                            String channelId,
                            String externalMessageId,
                            String lockToken) {
    }
}
