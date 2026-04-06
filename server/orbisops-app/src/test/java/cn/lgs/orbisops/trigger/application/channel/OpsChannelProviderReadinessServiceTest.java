package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelRuntimeReadPort;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderAdapter;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelReadinessSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsChannelProviderReadinessServiceTest {

    private ChannelRuntimeReadPort repository;
    private ChannelProviderAdapter adapter;
    private ChannelProviderConfiguration configuration;
    private OpsChannelProviderReadinessService service;

    @BeforeEach
    void setUp() {
        repository = mock(ChannelRuntimeReadPort.class);
        adapter = mock(ChannelProviderAdapter.class);
        configuration = mock(ChannelProviderConfiguration.class);
        when(adapter.type()).thenReturn(ChannelType.GENERIC_WEBHOOK);
        when(adapter.configuration(any())).thenReturn(configuration);
        when(repository.findById("channel-1")).thenReturn(Optional.of(channel(ChannelAccessPolicy.DENY_UNKNOWN)));
        service = new OpsChannelProviderReadinessService(repository, List.of(adapter));
    }

    @Test
    void readyRequiresConnectionTrafficAndIdentityEvidenceTogether() {
        when(adapter.preflight(configuration)).thenReturn(new ChannelHealthSnapshot(
                ChannelHealthSnapshot.HealthStatus.READY, "CONNECTED", "provider connected", Instant.now()));
        when(repository.findMessages("project-1", "channel-1", 100)).thenReturn(List.of(
                message("in-1", "INBOUND", "COMPLETED"),
                message("out-1", "OUTBOUND", "DELIVERED")));
        when(repository.findIdentities("project-1", "channel-1")).thenReturn(List.of(
                new ChannelIdentityRecord("mapping-1", "channel-1", "project-1", "external-user",
                        "user-1", "alice", ChannelStatus.ACTIVE, 1, "admin", null, null)));

        ChannelReadinessSnapshot result = service.readiness("project-1", "channel-1");

        assertTrue(result.ready());
        assertEquals(5, result.checks().size());
        assertTrue(result.checks().stream().allMatch(check -> check.status() == ChannelReadinessSnapshot.CheckStatus.PASS));
    }

    @Test
    void configuredButUntestedChannelNeverBecomesReady() {
        when(adapter.preflight(configuration)).thenReturn(new ChannelHealthSnapshot(
                ChannelHealthSnapshot.HealthStatus.UNKNOWN, "CONNECTION_NOT_STARTED", "not connected", Instant.now()));
        when(repository.findMessages("project-1", "channel-1", 100)).thenReturn(List.of());
        when(repository.findIdentities("project-1", "channel-1")).thenReturn(List.of());

        ChannelReadinessSnapshot result = service.readiness("project-1", "channel-1");

        assertFalse(result.ready());
        assertEquals(ChannelReadinessSnapshot.CheckStatus.PASS, check(result, ChannelReadinessSnapshot.CheckKind.CREDENTIALS).status());
        assertEquals(ChannelReadinessSnapshot.CheckStatus.ACTION_REQUIRED, check(result, ChannelReadinessSnapshot.CheckKind.CONNECTION).status());
        assertEquals(ChannelReadinessSnapshot.CheckStatus.ACTION_REQUIRED, check(result, ChannelReadinessSnapshot.CheckKind.INBOUND).status());
        assertEquals(ChannelReadinessSnapshot.CheckStatus.ACTION_REQUIRED, check(result, ChannelReadinessSnapshot.CheckKind.OUTBOUND).status());
        assertEquals(ChannelReadinessSnapshot.CheckStatus.ACTION_REQUIRED, check(result, ChannelReadinessSnapshot.CheckKind.IDENTITY_ACCESS).status());
    }

    @Test
    void unresolvedExternalCredentialIsExplicitlyBlocked() {
        when(adapter.preflight(configuration)).thenReturn(new ChannelHealthSnapshot(
                ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                "PROVIDER_CREDENTIAL_UNAVAILABLE", "external credential unavailable", Instant.now()));
        when(repository.findMessages("project-1", "channel-1", 100)).thenReturn(List.of());
        when(repository.findIdentities("project-1", "channel-1")).thenReturn(List.of());

        ChannelReadinessSnapshot result = service.readiness("project-1", "channel-1");

        assertFalse(result.ready());
        assertEquals(ChannelReadinessSnapshot.CheckStatus.BLOCKED_EXTERNAL,
                check(result, ChannelReadinessSnapshot.CheckKind.CREDENTIALS).status());
        assertEquals(ChannelReadinessSnapshot.CheckStatus.BLOCKED_EXTERNAL,
                check(result, ChannelReadinessSnapshot.CheckKind.CONNECTION).status());
    }

    @Test
    void observeOnlyUnknownCanSatisfyAccessCheckWithoutGrantingNormalIdentity() {
        when(repository.findById("channel-1")).thenReturn(Optional.of(channel(ChannelAccessPolicy.OBSERVE_ONLY_UNKNOWN)));
        when(adapter.preflight(configuration)).thenReturn(new ChannelHealthSnapshot(
                ChannelHealthSnapshot.HealthStatus.READY, "CONNECTED", "provider connected", Instant.now()));
        when(repository.findMessages("project-1", "channel-1", 100)).thenReturn(List.of(
                message("in-1", "INBOUND", "COMPLETED"),
                message("out-1", "OUTBOUND", "DELIVERED")));
        when(repository.findIdentities("project-1", "channel-1")).thenReturn(List.of());

        ChannelReadinessSnapshot result = service.readiness("project-1", "channel-1");

        assertTrue(result.ready());
        assertEquals("CHANNEL_OBSERVE_ONLY_UNKNOWN_ENABLED",
                check(result, ChannelReadinessSnapshot.CheckKind.IDENTITY_ACCESS).reasonCode());
    }

    private ChannelReadinessSnapshot.ChannelReadinessCheck check(ChannelReadinessSnapshot result,
                                                                  ChannelReadinessSnapshot.CheckKind kind) {
        return result.checks().stream().filter(item -> item.kind() == kind).findFirst().orElseThrow();
    }

    private ChannelRecord channel(ChannelAccessPolicy accessPolicy) {
        return new ChannelRecord("channel-1", "project-1", ExecutionBinding.react(), "Channel",
                "GENERIC_WEBHOOK", "credential-ref", Map.of(), accessPolicy, ChannelStatus.ACTIVE,
                "admin", null, null);
    }

    private ChannelMessageRecord message(String id, String direction, String status) {
        return new ChannelMessageRecord(id, "channel-1", "project-1", id + "-external",
                "conversation-1", direction.equals("INBOUND") ? "sender-1" : "platform",
                "session-1", "run-1", direction, status, "payload", null, null, null);
    }
}
