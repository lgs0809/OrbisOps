package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import cn.lgs.orbisops.types.execution.ExecutionType;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelManagementApplicationServiceTest {

    private IChannelRepository repository;
    private ChannelCatalogQuery catalog;
    private ChannelProtocolCatalogPort protocols;
    private ChannelExecutionBindingPort executionBindings;
    private ChannelIdentityDirectoryPort identities;
    private ChannelAuditPort audit;
    private ChannelRecoveryDispatchPort recoveryDispatch;
    private ChannelManagementApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(IChannelRepository.class);
        catalog = mock(ChannelCatalogQuery.class);
        protocols = mock(ChannelProtocolCatalogPort.class);
        executionBindings = mock(ChannelExecutionBindingPort.class);
        identities = mock(ChannelIdentityDirectoryPort.class);
        audit = mock(ChannelAuditPort.class);
        recoveryDispatch = mock(ChannelRecoveryDispatchPort.class);
        service = new ChannelManagementApplicationService(repository, catalog, protocols,
                executionBindings, identities, audit, recoveryDispatch);
    }

    @Test
    void createUsesAuthoritativeWorkflowBindingAndPersistsTypedRecord() {
        when(executionBindings.resolve("project-1", ExecutionBinding.workflow("agent-1", ExecutionVersionPolicy.LATEST_PUBLISHED, 99, "")))
                .thenReturn(new ChannelExecutionBindingPort.ResolvedExecution(ExecutionType.WORKFLOW, "agent-1", 7, "published-hash"));
        Map<String, Object> storedView = Map.of(
                "channelId", "channel-1", "projectId", "project-1", "executionType", "WORKFLOW",
                "workflowId", "agent-1", "workflowVersion", 7, "status", "ACTIVE");
        when(catalog.get("project-1", "channel-1")).thenReturn(storedView);

        Map<String, Object> result = service.create(configuration(
                "", "",
                supplied("project-1"), supplied("channel-1"), supplied(ExecutionType.WORKFLOW), supplied("agent-1"),
                absentVersionPolicy(), supplied(99), supplied("Oncall"), supplied("generic_webhook"), absentText(),
                supplied(Map.of("outboundUrl", "https://bridge.test/send")), absentStatus()));

        ArgumentCaptor<ChannelRecord> record = ArgumentCaptor.forClass(ChannelRecord.class);
        verify(repository).create(record.capture());
        assertEquals(7, record.getValue().inboundExecution().version());
        assertEquals("published-hash", record.getValue().inboundExecution().definitionHash());
        assertEquals(ExecutionVersionPolicy.LATEST_PUBLISHED, record.getValue().inboundExecution().versionPolicy());
        assertEquals(ChannelStatus.ACTIVE, record.getValue().status());
        assertEquals("GENERIC_WEBHOOK", record.getValue().channelType());
        assertEquals(Map.of("outboundUrl", "https://bridge.test/send"), record.getValue().config());
        verify(protocols).validateConfiguration(record.getValue());
        verify(audit).record("project-1", "channel", "create", "channel-1", null, storedView);
        assertEquals(storedView, result);
    }

    @Test
    void providerValidationFailureNeverPersistsOrAuditsCandidate() {
        doThrow(new IllegalArgumentException("CHANNEL_CREDENTIAL_MUST_USE_REFERENCE"))
                .when(protocols).validateConfiguration(any());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.create(configuration(
                        "", "",
                        supplied("project-1"), supplied("channel-secret"), supplied(ExecutionType.REACT), absentText(),
                        absentVersionPolicy(), absentInteger(), supplied("Unsafe"), supplied("GENERIC_WEBHOOK"),
                        supplied("plaintext-secret"), absentMap(), absentStatus())));

        assertEquals("CHANNEL_CREDENTIAL_MUST_USE_REFERENCE", failure.getMessage());
        verify(repository, never()).create(any());
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void outputOnlyChannelDoesNotRequireExecutionDefinition() {
        Map<String, Object> storedView = Map.of("channelId", "notify-only", "executionType", "NONE");
        when(catalog.get("project-1", "notify-only")).thenReturn(storedView);

        service.create(configuration("", "", supplied("project-1"), supplied("notify-only"),
                supplied(ExecutionType.NONE), absentText(), absentVersionPolicy(), absentInteger(),
                supplied("Notify only"), supplied("GENERIC_WEBHOOK"), absentText(), absentMap(), absentStatus()));

        ArgumentCaptor<ChannelRecord> record = ArgumentCaptor.forClass(ChannelRecord.class);
        verify(repository).create(record.capture());
        assertEquals(ExecutionType.NONE, record.getValue().inboundExecution().type());
        verify(executionBindings, never()).resolve(any(), any());
    }

    @Test
    void reactChannelPersistsProductBindingWithoutInternalDefaultAgent() {
        Map<String, Object> storedView = Map.of("channelId", "react-channel", "executionType", "REACT");
        when(catalog.get("project-1", "react-channel")).thenReturn(storedView);

        service.create(configuration("", "", supplied("project-1"), supplied("react-channel"),
                supplied(ExecutionType.REACT), absentText(), absentVersionPolicy(), absentInteger(),
                supplied("React input"), supplied("GENERIC_WEBHOOK"), absentText(), absentMap(), absentStatus()));

        ArgumentCaptor<ChannelRecord> record = ArgumentCaptor.forClass(ChannelRecord.class);
        verify(repository).create(record.capture());
        assertEquals(ExecutionBinding.react(), record.getValue().inboundExecution());
        verify(executionBindings, never()).resolve(any(), any());
    }

    @Test
    void bindIdentityRequiresProjectMembershipAndUsesVersionCas() {
        when(repository.findById("channel-1")).thenReturn(Optional.of(channel()));
        ChannelIdentityRecord existing = new ChannelIdentityRecord(
                "mapping-1", "channel-1", "project-1", "sender-1", "user-old", "old",
                ChannelStatus.ACTIVE, 3, "admin", null, null);
        when(repository.findIdentity("channel-1", "sender-1")).thenReturn(Optional.of(existing));
        ChannelIdentityDirectoryPort.PlatformIdentity identity =
                new ChannelIdentityDirectoryPort.PlatformIdentity("user-1", "alice");
        when(identities.resolveActive("user-1", "alice")).thenReturn(identity);
        when(identities.canAccessProject("project-1", identity)).thenReturn(true);
        when(repository.updateIdentity(any(), org.mockito.ArgumentMatchers.eq(3L))).thenReturn(true);

        Map<String, Object> result = service.bindIdentity(new ChannelModels.IdentityBinding(
                "project-1", "channel-1", "sender-1", "user-1", "alice",
                ChannelStatus.ACTIVE, 3, "admin"));

        ArgumentCaptor<ChannelIdentityRecord> updated = ArgumentCaptor.forClass(ChannelIdentityRecord.class);
        verify(repository).updateIdentity(updated.capture(), org.mockito.ArgumentMatchers.eq(3L));
        assertEquals(4L, updated.getValue().version());
        assertEquals("user-1", updated.getValue().platformUserId());
        assertEquals("user-1", result.get("platformUserId"));
        verify(audit).record(org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq("channel-identity"), org.mockito.ArgumentMatchers.eq("update"),
                org.mockito.ArgumentMatchers.eq("mapping-1"), any(), any());
    }

    @Test
    void bindIdentityRejectsUserOutsideProjectBeforeMutation() {
        when(repository.findById("channel-1")).thenReturn(Optional.of(channel()));
        when(repository.findIdentity("channel-1", "sender-1")).thenReturn(Optional.empty());
        ChannelIdentityDirectoryPort.PlatformIdentity identity =
                new ChannelIdentityDirectoryPort.PlatformIdentity("user-1", "alice");
        when(identities.resolveActive("user-1", "alice")).thenReturn(identity);
        when(identities.canAccessProject("project-1", identity)).thenReturn(false);

        SecurityException failure = assertThrows(SecurityException.class, () -> service.bindIdentity(
                new ChannelModels.IdentityBinding("project-1", "channel-1", "sender-1", "user-1", "alice",
                        ChannelStatus.ACTIVE, 0, "admin")));

        assertEquals("CHANNEL_IDENTITY_USER_NOT_PROJECT_MEMBER", failure.getMessage());
        verify(repository, never()).createIdentity(any());
        verify(repository, never()).updateIdentity(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void recoveryDispatchesOnlyAfterDurableStateTransition() {
        when(repository.findById("channel-1")).thenReturn(Optional.of(channel()));
        ChannelModels.Recovery command = new ChannelModels.Recovery(
                "project-1", "channel-1", "external-1", true, "admin");
        ChannelMessageRecord recoveryRequired = message("RECOVERY_REQUIRED");
        ChannelMessageRecord queued = message("QUEUED");
        when(repository.findInbound("channel-1", "external-1"))
                .thenReturn(Optional.of(recoveryRequired), Optional.of(queued));
        when(repository.requeueRecoveryRequired("project-1", "channel-1", "external-1")).thenReturn(true);

        Map<String, Object> result = service.requeue(command);

        assertEquals("QUEUED", result.get("status"));
        verify(recoveryDispatch).dispatch("channel-1", "external-1");
        verify(audit).record(org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq("channel-inbound"),
                org.mockito.ArgumentMatchers.eq("requeue-recovery"),
                org.mockito.ArgumentMatchers.eq("external-1"), any(), any());
    }

    @Test
    void recoveryStateConflictNeverDispatches() {
        when(repository.findById("channel-1")).thenReturn(Optional.of(channel()));
        ChannelModels.Recovery command = new ChannelModels.Recovery(
                "project-1", "channel-1", "external-1", true, "admin");
        when(repository.findInbound("channel-1", "external-1"))
                .thenReturn(Optional.of(message("RECOVERY_REQUIRED")));
        when(repository.requeueRecoveryRequired("project-1", "channel-1", "external-1")).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.requeue(command));

        verify(recoveryDispatch, never()).dispatch(any(), any());
    }

    @Test
    void pinnedChannelRequiresExplicitPublishedVersion() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.create(configuration("", "", supplied("project-1"), absentText(),
                        supplied(ExecutionType.WORKFLOW), supplied("agent-1"), supplied(ExecutionVersionPolicy.PINNED_VERSION), absentInteger(),
                        supplied("Pinned channel"), supplied("GENERIC_WEBHOOK"), absentText(),
                        absentMap(), absentStatus())));

        assertEquals("WORKFLOW_PINNED_VERSION_REQUIRED", failure.getMessage());
        verify(executionBindings, never()).resolve(any(), any());
        verify(repository, never()).create(any());
    }

    @Test
    void updateRejectsPlatformOwnedIdentityAndProtocolMutation() {
        when(repository.findById("channel-1")).thenReturn(Optional.of(channel()));
        when(catalog.get("project-1", "channel-1")).thenReturn(Map.of("channelId", "channel-1"));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.update(configuration("project-1", "channel-1", supplied("project-other"),
                        absentText(), absentExecutionType(), absentText(), absentVersionPolicy(), absentInteger(), absentText(), supplied("OTHER"),
                        absentText(), absentMap(), absentStatus())));

        assertEquals("CHANNEL_IDENTITY_IMMUTABLE:projectId", failure.getMessage());
        verify(repository, never()).update(any());
    }

    @Test
    void recoveryRequiresExplicitNoSideEffectConfirmation() {
        when(repository.findById("channel-1")).thenReturn(Optional.of(channel()));
        ChannelModels.Recovery command = new ChannelModels.Recovery(
                "project-1", "channel-1", "external-1", false, "admin");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.requeue(command));

        assertEquals("CHANNEL_RECOVERY_CONFIRMATION_REQUIRED", failure.getMessage());
        verify(repository, never()).requeueRecoveryRequired(any(), any(), any());
        verify(recoveryDispatch, never()).dispatch(any(), any());
    }

    @Test
    void disablingExistingIdentityDoesNotRequireActivePlatformUserLookup() {
        when(repository.findById("channel-1")).thenReturn(Optional.of(channel()));
        ChannelIdentityRecord existing = new ChannelIdentityRecord(
                "mapping-1", "channel-1", "project-1", "sender-1", "user-1", "alice",
                ChannelStatus.ACTIVE, 4, "admin", null, null);
        when(repository.findIdentity("channel-1", "sender-1")).thenReturn(Optional.of(existing));
        when(repository.updateIdentity(any(), org.mockito.ArgumentMatchers.eq(4L))).thenReturn(true);

        Map<String, Object> result = service.bindIdentity(new ChannelModels.IdentityBinding(
                "project-1", "channel-1", "sender-1", "", "", ChannelStatus.DISABLED, 4, "admin"));

        assertEquals("DISABLED", result.get("status"));
        verify(identities, never()).resolveActive(any(), any());
        verify(identities, never()).canAccessProject(any(), any());
        verify(repository).updateIdentity(any(), org.mockito.ArgumentMatchers.eq(4L));
    }

    private ChannelModels.ConfigurationMutation configuration(
            String projectId,
            String channelId,
            ChannelModels.Field<String> requestedProjectId,
            ChannelModels.Field<String> requestedChannelId,
            ChannelModels.Field<ExecutionType> executionType,
            ChannelModels.Field<String> workflowId,
            ChannelModels.Field<ExecutionVersionPolicy> versionPolicy,
            ChannelModels.Field<Integer> version,
            ChannelModels.Field<String> name,
            ChannelModels.Field<String> type,
            ChannelModels.Field<String> credentialRef,
            ChannelModels.Field<Map<String, Object>> config,
            ChannelModels.Field<ChannelStatus> status) {
        return new ChannelModels.ConfigurationMutation(projectId, channelId, requestedProjectId, requestedChannelId,
                executionType, workflowId, versionPolicy, version, name, type, credentialRef, config,
                ChannelModels.Field.absent(), status, "admin");
    }

    private ChannelRecord channel() {
        return new ChannelRecord("channel-1", "project-1",
                ExecutionBinding.workflow("agent-1", ExecutionVersionPolicy.LATEST_PUBLISHED, 3, "agent-hash"),
                "Oncall", "GENERIC_WEBHOOK", "", Map.of(), ChannelStatus.ACTIVE, "admin", null, null);
    }

    private ChannelMessageRecord message(String status) {
        return new ChannelMessageRecord("message-1", "channel-1", "project-1", "external-1",
                "conversation-1", "sender-1", "session-1", "run-1", "INBOUND", status,
                "payload", null, null, null);
    }

    private static <T> ChannelModels.Field<T> supplied(T value) {
        return ChannelModels.Field.supplied(value);
    }

    private static ChannelModels.Field<String> absentText() {
        return ChannelModels.Field.absent();
    }

    private static ChannelModels.Field<Integer> absentInteger() {
        return ChannelModels.Field.absent();
    }

    private static ChannelModels.Field<Map<String, Object>> absentMap() {
        return ChannelModels.Field.absent();
    }

    private static ChannelModels.Field<ChannelStatus> absentStatus() {
        return ChannelModels.Field.absent();
    }

    private static ChannelModels.Field<ExecutionType> absentExecutionType() {
        return ChannelModels.Field.absent();
    }

    private static ChannelModels.Field<ExecutionVersionPolicy> absentVersionPolicy() {
        return ChannelModels.Field.absent();
    }
}
