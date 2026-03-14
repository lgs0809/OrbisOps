package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelRuntimeReadPort;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderAdapter;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelReadinessSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelReadinessSnapshot.ChannelReadinessCheck;
import cn.lgs.orbisops.application.channel.provider.ChannelReadinessSnapshot.CheckKind;
import cn.lgs.orbisops.application.channel.provider.ChannelReadinessSnapshot.CheckStatus;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public final class OpsChannelProviderReadinessService {

    private final ChannelRuntimeReadPort repository;
    private final Map<ChannelType, ChannelProviderAdapter<?>> adapters;

    public OpsChannelProviderReadinessService(ChannelRuntimeReadPort repository,
                                              List<ChannelProviderAdapter<?>> adapters) {
        if (repository == null) throw new IllegalArgumentException("CHANNEL_REPOSITORY_REQUIRED");
        this.repository = repository;
        Map<ChannelType, ChannelProviderAdapter<?>> index = new HashMap<>();
        if (adapters != null) adapters.forEach(adapter -> index.put(adapter.type(), adapter));
        this.adapters = Map.copyOf(index);
    }

    public ChannelReadinessSnapshot readiness(String projectId, String channelId) {
        String safeProjectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        String safeChannelId = required(channelId, "CHANNEL_ID_REQUIRED");
        ChannelRecord channel = repository.findById(safeChannelId)
                .orElseThrow(() -> new IllegalArgumentException("CHANNEL_NOT_FOUND"));
        if (!safeProjectId.equals(channel.projectId())) throw new SecurityException("CHANNEL_PROJECT_MISMATCH");
        ChannelType type = ChannelType.parse(channel.channelType());
        ChannelProviderAdapter<?> adapter = adapters.get(type);
        if (adapter == null) {
            List<ChannelReadinessCheck> unavailable = List.of(
                    check(CheckKind.CREDENTIALS, CheckStatus.BLOCKED_EXTERNAL, "CHANNEL_PROVIDER_NOT_CONFIGURED", "Provider adapter is not installed"),
                    check(CheckKind.CONNECTION, CheckStatus.BLOCKED_EXTERNAL, "CHANNEL_PROVIDER_NOT_CONFIGURED", "Provider adapter is not installed"),
                    check(CheckKind.INBOUND, CheckStatus.ACTION_REQUIRED, "CHANNEL_INBOUND_NOT_VERIFIED", "No provider adapter available"),
                    check(CheckKind.OUTBOUND, CheckStatus.ACTION_REQUIRED, "CHANNEL_OUTBOUND_NOT_VERIFIED", "No provider adapter available"),
                    accessCheck(channel, repository.findIdentities(safeProjectId, safeChannelId)));
            return new ChannelReadinessSnapshot(safeChannelId, safeProjectId, type, false, unavailable, Instant.now());
        }

        List<ChannelReadinessCheck> checks = new ArrayList<>();
        ChannelHealthSnapshot health;
        try {
            health = preflight(adapter, channel);
            checks.add(credentialsCheck(health));
            checks.add(connectionCheck(health));
        } catch (RuntimeException invalid) {
            String reason = safe(invalid.getMessage());
            checks.add(check(CheckKind.CREDENTIALS, CheckStatus.BLOCKED_EXTERNAL,
                    reason.isBlank() ? "CHANNEL_CONFIGURATION_INVALID" : reason,
                    "Provider configuration or credential reference is invalid"));
            checks.add(check(CheckKind.CONNECTION, CheckStatus.BLOCKED_EXTERNAL,
                    "CHANNEL_CONNECTION_BLOCKED_BY_CONFIGURATION", "Fix provider configuration before testing connection"));
        }

        List<ChannelMessageRecord> messages = repository.findMessages(safeProjectId, safeChannelId, 100);
        boolean inboundObserved = messages.stream().anyMatch(this::successfulInbound);
        boolean outboundObserved = messages.stream().anyMatch(this::successfulOutbound);
        checks.add(check(CheckKind.INBOUND,
                inboundObserved ? CheckStatus.PASS : CheckStatus.ACTION_REQUIRED,
                inboundObserved ? "CHANNEL_INBOUND_OBSERVED" : "CHANNEL_INBOUND_TEST_REQUIRED",
                inboundObserved ? "Authenticated inbound message has entered the durable Channel pipeline" : "Send a real provider message to verify inbound delivery"));
        checks.add(check(CheckKind.OUTBOUND,
                outboundObserved ? CheckStatus.PASS : CheckStatus.ACTION_REQUIRED,
                outboundObserved ? "CHANNEL_OUTBOUND_DELIVERED" : "CHANNEL_OUTBOUND_TEST_REQUIRED",
                outboundObserved ? "At least one outbound message was acknowledged as delivered" : "Run a real outbound test to verify delivery"));
        checks.add(accessCheck(channel, repository.findIdentities(safeProjectId, safeChannelId)));
        return new ChannelReadinessSnapshot(safeChannelId, safeProjectId, type, false, checks, Instant.now());
    }

    private ChannelReadinessCheck credentialsCheck(ChannelHealthSnapshot health) {
        if (health.status() == ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL
                && credentialReason(health.reasonCode())) {
            return check(CheckKind.CREDENTIALS, CheckStatus.BLOCKED_EXTERNAL, health.reasonCode(), health.detail());
        }
        return check(CheckKind.CREDENTIALS, CheckStatus.PASS,
                "CHANNEL_CREDENTIAL_REFERENCE_RESOLVED", "Credential reference and provider configuration are accepted");
    }

    private ChannelReadinessCheck connectionCheck(ChannelHealthSnapshot health) {
        return switch (health.status()) {
            case READY -> check(CheckKind.CONNECTION, CheckStatus.PASS, health.reasonCode(), health.detail());
            case BLOCKED_EXTERNAL -> check(CheckKind.CONNECTION, CheckStatus.BLOCKED_EXTERNAL, health.reasonCode(), health.detail());
            case DEGRADED, STOPPED, UNKNOWN -> check(CheckKind.CONNECTION, CheckStatus.ACTION_REQUIRED,
                    health.reasonCode(), health.detail());
        };
    }

    private ChannelReadinessCheck accessCheck(ChannelRecord channel, List<ChannelIdentityRecord> identities) {
        long active = identities == null ? 0 : identities.stream().filter(item -> item.status() == ChannelStatus.ACTIVE).count();
        if (channel.accessPolicy() == ChannelAccessPolicy.OBSERVE_ONLY_UNKNOWN) {
            return check(CheckKind.IDENTITY_ACCESS, CheckStatus.PASS, "CHANNEL_OBSERVE_ONLY_UNKNOWN_ENABLED",
                    "Unknown senders are hard-downgraded to read-only; mapped identities retain normal project authorization");
        }
        if (active > 0) {
            return check(CheckKind.IDENTITY_ACCESS, CheckStatus.PASS, "CHANNEL_IDENTITY_MAPPING_AVAILABLE",
                    active + " active identity mapping(s) available");
        }
        return check(CheckKind.IDENTITY_ACCESS, CheckStatus.ACTION_REQUIRED,
                "CHANNEL_IDENTITY_MAPPING_REQUIRED", "Bind at least one authorized project member before declaring the Channel ready");
    }

    private boolean successfulInbound(ChannelMessageRecord message) {
        if (message == null || !"INBOUND".equalsIgnoreCase(message.direction())) return false;
        String status = safe(message.status()).toUpperCase();
        return !status.contains("FAILED") && !status.contains("REJECTED") && !status.contains("RECOVERY_REQUIRED");
    }

    private boolean successfulOutbound(ChannelMessageRecord message) {
        if (message == null || !"OUTBOUND".equalsIgnoreCase(message.direction())) return false;
        String status = safe(message.status()).toUpperCase();
        return "DELIVERED".equals(status) || "COMPLETED".equals(status) || "UPDATED".equals(status) || "REPLIED".equals(status);
    }

    private boolean credentialReason(String reasonCode) {
        String normalized = safe(reasonCode).toUpperCase();
        return normalized.contains("SECRET") || normalized.contains("CREDENTIAL") || normalized.contains("AUTHENTICATION_FAILED");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ChannelHealthSnapshot preflight(ChannelProviderAdapter adapter, ChannelRecord channel) {
        ChannelProviderConfiguration configuration = (ChannelProviderConfiguration) adapter.configuration(channel);
        adapter.validateConfiguration(configuration);
        return adapter.preflight(configuration);
    }

    private ChannelReadinessCheck check(CheckKind kind, CheckStatus status, String reasonCode, String detail) {
        return new ChannelReadinessCheck(kind, status, reasonCode, detail);
    }

    private String required(String value, String reasonCode) {
        String normalized = safe(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
