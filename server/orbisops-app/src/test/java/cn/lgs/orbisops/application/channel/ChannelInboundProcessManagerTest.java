package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import cn.lgs.orbisops.types.execution.ExecutionType;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelInboundProcessManagerTest {

    private IChannelRepository repository;
    private ChannelInboundProtocolPort protocol;
    private ChannelConversationLeaseService leases;
    private ChannelExecutionBindingPort bindings;
    private ChannelIdentityDirectoryPort identities;
    private ChannelAgentChatPort chat;
    private ChannelReplyOutboxPort replies;
    private ChannelRuntimeAuditPort audit;
    private ChannelTaskExecutorPort executor;
    private ChannelInboundProcessManager manager;

    @BeforeEach
    void setUp() {
        repository = mock(IChannelRepository.class);
        protocol = mock(ChannelInboundProtocolPort.class);
        leases = mock(ChannelConversationLeaseService.class);
        bindings = mock(ChannelExecutionBindingPort.class);
        identities = mock(ChannelIdentityDirectoryPort.class);
        chat = mock(ChannelAgentChatPort.class);
        replies = mock(ChannelReplyOutboxPort.class);
        audit = mock(ChannelRuntimeAuditPort.class);
        executor = mock(ChannelTaskExecutorPort.class);
        when(repository.findById("channel-1")).thenReturn(Optional.of(channel()));
        manager = new ChannelInboundProcessManager(repository, protocol, leases, bindings, identities,
                chat, replies, audit, executor, new ChannelOutboundContentPolicy(), true, 20, 300);
    }

    @Test
    void receivePersistsAndAuditsBeforeScheduling() {
        when(protocol.verifyAndProtect(any(), any(), any(), any()))
                .thenReturn(new ChannelInboundProtocolPort.VerifiedInbound("encrypted-envelope"));
        when(repository.insertInbound(any())).thenReturn(true);
        when(repository.resolveSession(any(), any(), any(), any(), any(), any())).thenReturn("session-1");

        Map<String, Object> result = manager.receive(receive());

        ArgumentCaptor<ChannelMessageRecord> inserted = ArgumentCaptor.forClass(ChannelMessageRecord.class);
        verify(repository).insertInbound(inserted.capture());
        assertEquals("RECEIVED", inserted.getValue().status());
        assertEquals("encrypted-envelope", inserted.getValue().payloadJson());
        verify(repository).markInboundQueued(org.mockito.ArgumentMatchers.eq("channel-1"),
                org.mockito.ArgumentMatchers.eq("external-1"), any(), org.mockito.ArgumentMatchers.eq("session-1"));
        verify(audit).record(org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq("agent-1"), any(),
                org.mockito.ArgumentMatchers.eq("CHANNEL_MESSAGE_ACCEPTED"),
                org.mockito.ArgumentMatchers.eq("external-1"), org.mockito.ArgumentMatchers.eq("LOW"),
                org.mockito.ArgumentMatchers.eq("QUEUED"), any());
        verify(executor).execute(any());
        assertEquals("ACCEPTED", result.get("status"));
    }

    @Test
    void providerThreadUsesRootInSessionKeyWithoutChangingStoredProviderConversationId() {
        var conversation = new cn.lgs.orbisops.application.channel.provider.ChannelConversationRef(
                "conversation-1", cn.lgs.orbisops.application.channel.provider.ChannelConversationRef.ConversationKind.GROUP);
        var envelope = new cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope(
                new cn.lgs.orbisops.application.channel.provider.ChannelMessageRef("external-thread-1", conversation, "thread-root-1"),
                new cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal(
                        "sender-1", "Alice", cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal.PrincipalKind.USER),
                cn.lgs.orbisops.application.channel.provider.ChannelRichContent.text("continue"),
                List.of(), Instant.now(), "provider-envelope-1");
        ArgumentCaptor<ChannelModels.InboundMessage> protectedMessage = ArgumentCaptor.forClass(ChannelModels.InboundMessage.class);
        when(protocol.protectTrusted(any(), protectedMessage.capture()))
                .thenReturn(new ChannelInboundProtocolPort.VerifiedInbound("encrypted-thread-envelope"));
        when(repository.insertInbound(any())).thenReturn(true);
        when(repository.resolveSession(any(), any(), any(), any(), any(), any())).thenReturn("session-thread-1");

        manager.receiveProvider("channel-1", envelope);

        assertEquals("thread-root-1", protectedMessage.getValue().metadata().get("threadId"));
        verify(repository).resolveSession(
                org.mockito.ArgumentMatchers.eq("channel-1"),
                org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq("agent-1"),
                org.mockito.ArgumentMatchers.eq("conversation-1\nthread:thread-root-1"),
                org.mockito.ArgumentMatchers.eq("sender-1"),
                org.mockito.ArgumentMatchers.anyString());
        ArgumentCaptor<ChannelMessageRecord> stored = ArgumentCaptor.forClass(ChannelMessageRecord.class);
        verify(repository).insertInbound(stored.capture());
        assertEquals("conversation-1", stored.getValue().externalConversationId());
    }

    @Test
    void restoredThreadMessageRepliesToRootInsteadOfNestedMessageId() {
        ChannelMessageRecord stored = queued();
        ChannelConversationLeaseService.LeaseHandle lease = lease();
        ChannelModels.InboundMessage threadMessage = new ChannelModels.InboundMessage(
                "external-1", "conversation-1", "sender-1", "continue", 100,
                Map.of("providerAuthenticated", true, "threadId", "thread-root-1"), "TEXT", List.of(), null);
        when(repository.findInbound("channel-1", "external-1")).thenReturn(Optional.of(stored));
        when(leases.acquire(any(), any())).thenReturn(Optional.of(lease));
        when(protocol.restore(stored)).thenReturn(threadMessage);
        when(bindings.resolve("project-1", channel().inboundExecution()))
                .thenReturn(new ChannelExecutionBindingPort.ResolvedExecution(ExecutionType.WORKFLOW, "agent-1", 3, "agent-hash"));
        when(repository.findIdentity("channel-1", "sender-1")).thenReturn(Optional.empty());
        when(chat.chat(any())).thenReturn(new ChannelAgentChatPort.ChatResult("answer", "channel-user"));
        when(replies.enqueue(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ChannelReplyOutboxPort.ReplyDelivery(false, false));
        when(leases.complete(lease, "REPLY_QUEUED", "run-1", "session-1")).thenReturn(true);

        manager.processQueued("channel-1", "external-1");

        verify(replies).enqueue("project-1", "channel-1", "conversation-1", "answer",
                "run-1", "session-1", "thread-root-1");
    }

    @Test
    void outputOnlyChannelRejectsInboundBeforeProtocolOrQueue() {
        when(repository.findById("channel-1")).thenReturn(Optional.of(new ChannelRecord(
                "channel-1", "project-1", ExecutionBinding.none(), "Notify only", "GENERIC_WEBHOOK",
                "credential-ref", Map.of("outboundUrl", "https://bridge.test/reply"), ChannelStatus.ACTIVE,
                "admin", null, null)));

        SecurityException failure = assertThrows(SecurityException.class, () -> manager.receive(receive()));

        assertEquals("CHANNEL_INBOUND_DISABLED", failure.getMessage());
        verify(protocol, never()).verifyAndProtect(any(), any(), any(), any());
        verify(repository, never()).insertInbound(any());
        verify(executor, never()).execute(any());
    }

    @Test
    void duplicateReceiveNeverSchedulesAnotherExecution() {
        when(protocol.verifyAndProtect(any(), any(), any(), any()))
                .thenReturn(new ChannelInboundProtocolPort.VerifiedInbound("encrypted-envelope"));
        when(repository.insertInbound(any())).thenReturn(false);

        Map<String, Object> result = manager.receive(receive());

        assertEquals("DUPLICATE_IGNORED", result.get("status"));
        verify(executor, never()).execute(any());
        verify(repository, never()).markInboundQueued(any(), any(), any(), any());
    }

    @Test
    void acceptanceAuditFailureMarksMessageFailedAndStopsExecution() {
        when(protocol.verifyAndProtect(any(), any(), any(), any()))
                .thenReturn(new ChannelInboundProtocolPort.VerifiedInbound("encrypted-envelope"));
        when(repository.insertInbound(any())).thenReturn(true);
        when(repository.resolveSession(any(), any(), any(), any(), any(), any())).thenReturn("session-1");
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(
                any(), any(), any(), org.mockito.ArgumentMatchers.eq("CHANNEL_MESSAGE_ACCEPTED"),
                any(), any(), any(), any());

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> manager.receive(receive()));

        assertEquals("CHANNEL_ACCEPTANCE_AUDIT_FAILED", failure.getMessage());
        verify(repository).markInboundFailed("channel-1", "external-1", "CHANNEL_ACCEPTANCE_AUDIT_FAILED");
        verify(executor, never()).execute(any());
    }

    @Test
    void leaseConflictLeavesQueuedMessageWithoutCallingChat() {
        ChannelMessageRecord stored = queued();
        when(repository.findInbound("channel-1", "external-1")).thenReturn(Optional.of(stored));
        when(leases.acquire(any(), any())).thenReturn(Optional.empty());

        manager.processQueued("channel-1", "external-1");

        verify(chat, never()).chat(any());
        verify(leases, never()).release(any());
    }

    @Test
    void successfulClaimedMessageWritesReplyAndTerminalStatus() {
        ChannelMessageRecord stored = queued();
        ChannelConversationLeaseService.LeaseHandle lease = lease();
        when(repository.findInbound("channel-1", "external-1")).thenReturn(Optional.of(stored));
        when(leases.acquire(any(), any())).thenReturn(Optional.of(lease));
        when(protocol.restore(stored)).thenReturn(message());
        when(bindings.resolve("project-1", channel().inboundExecution()))
                .thenReturn(new ChannelExecutionBindingPort.ResolvedExecution(ExecutionType.WORKFLOW, "agent-1", 3, "agent-hash"));
        when(repository.findIdentity("channel-1", "sender-1")).thenReturn(Optional.empty());
        when(chat.chat(any())).thenReturn(new ChannelAgentChatPort.ChatResult("answer", "channel-user"));
        when(replies.enqueue(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ChannelReplyOutboxPort.ReplyDelivery(false, false));
        when(leases.complete(lease, "REPLY_QUEUED", "run-1", "session-1")).thenReturn(true);

        manager.processQueued("channel-1", "external-1");

        ArgumentCaptor<ChannelAgentChatPort.ChatCommand> command =
                ArgumentCaptor.forClass(ChannelAgentChatPort.ChatCommand.class);
        verify(chat).chat(command.capture());
        assertEquals(ChannelAgentChatPort.IdentityStatus.UNMAPPED, command.getValue().identityStatus());
        assertEquals(ChannelAgentChatPort.RuntimeAccess.OBSERVE_ONLY_UNKNOWN, command.getValue().runtimeAccess());
        verify(replies).enqueue("project-1", "channel-1", "conversation-1", "answer",
                "run-1", "session-1", "external-1");
        verify(leases).complete(lease, "REPLY_QUEUED", "run-1", "session-1");
        verify(leases).release(lease);
        verify(audit).record("project-1", "agent-1", "channel-user", "CHANNEL_MESSAGE_COMPLETED",
                "external-1", "LOW", "SUCCEEDED",
                Map.of("channelId", "channel-1", "runId", "run-1", "sessionId", "session-1"));
    }

    @Test
    void unknownSenderIsDeniedByDefaultBeforeExecutionResolution() {
        ChannelMessageRecord stored = queued();
        ChannelConversationLeaseService.LeaseHandle lease = lease();
        when(repository.findById("channel-1")).thenReturn(Optional.of(channelWithPolicy(ChannelAccessPolicy.DENY_UNKNOWN)));
        when(repository.findInbound("channel-1", "external-1")).thenReturn(Optional.of(stored));
        when(leases.acquire(any(), any())).thenReturn(Optional.of(lease));
        when(protocol.restore(stored)).thenReturn(message());
        when(repository.findIdentity("channel-1", "sender-1")).thenReturn(Optional.empty());
        when(leases.fail(lease, "CHANNEL_UNKNOWN_SENDER_DENIED")).thenReturn(true);

        manager.processQueued("channel-1", "external-1");

        verify(bindings, never()).resolve(any(), any());
        verify(chat, never()).chat(any());
        verify(replies, never()).enqueue(any(), any(), any(), any(), any(), any(), any());
        verify(leases).fail(lease, "CHANNEL_UNKNOWN_SENDER_DENIED");
        verify(audit).record(org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq("agent-1"), any(),
                org.mockito.ArgumentMatchers.eq("CHANNEL_UNKNOWN_SENDER_DENIED"),
                org.mockito.ArgumentMatchers.eq("external-1"), org.mockito.ArgumentMatchers.eq("MEDIUM"),
                org.mockito.ArgumentMatchers.eq("BLOCKED"), any());
    }

    @Test
    void pairingPolicyReturnsSafePairingGuidanceWithoutEnteringAgentRuntime() {
        ChannelMessageRecord stored = queued();
        ChannelConversationLeaseService.LeaseHandle lease = lease();
        when(repository.findById("channel-1")).thenReturn(Optional.of(channelWithPolicy(ChannelAccessPolicy.PAIRING)));
        when(repository.findInbound("channel-1", "external-1")).thenReturn(Optional.of(stored));
        when(leases.acquire(any(), any())).thenReturn(Optional.of(lease));
        when(protocol.restore(stored)).thenReturn(message());
        when(repository.findIdentity("channel-1", "sender-1")).thenReturn(Optional.empty());
        when(replies.enqueue(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ChannelReplyOutboxPort.ReplyDelivery(false, false));
        when(leases.complete(lease, "REPLY_QUEUED", "run-1", "session-1")).thenReturn(true);

        manager.processQueued("channel-1", "external-1");

        verify(bindings, never()).resolve(any(), any());
        verify(chat, never()).chat(any());
        verify(replies).enqueue("project-1", "channel-1", "conversation-1",
                "身份尚未绑定，请联系项目管理员完成 Channel 身份绑定后再使用。",
                "run-1", "session-1", "external-1");
        verify(leases).complete(lease, "REPLY_QUEUED", "run-1", "session-1");
    }

    @Test
    void mappedSenderRemainsAuthenticatedEvenWhenUnknownSendersAreDenied() {
        ChannelMessageRecord stored = queued();
        ChannelConversationLeaseService.LeaseHandle lease = lease();
        ChannelIdentityRecord identity = new ChannelIdentityRecord("mapping-1", "channel-1", "project-1",
                "sender-1", "user-1", "alice", ChannelStatus.ACTIVE, 1, "admin", null, null);
        when(repository.findById("channel-1")).thenReturn(Optional.of(channelWithPolicy(ChannelAccessPolicy.DENY_UNKNOWN)));
        when(repository.findInbound("channel-1", "external-1")).thenReturn(Optional.of(stored));
        when(leases.acquire(any(), any())).thenReturn(Optional.of(lease));
        when(protocol.restore(stored)).thenReturn(message());
        when(repository.findIdentity("channel-1", "sender-1")).thenReturn(Optional.of(identity));
        when(identities.isTrusted("project-1", identity)).thenReturn(true);
        when(bindings.resolve(any(), any()))
                .thenReturn(new ChannelExecutionBindingPort.ResolvedExecution(ExecutionType.WORKFLOW, "agent-1", 3, "agent-hash"));
        when(chat.chat(any())).thenReturn(new ChannelAgentChatPort.ChatResult("answer", "user-1"));
        when(replies.enqueue(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ChannelReplyOutboxPort.ReplyDelivery(true, false));
        when(leases.complete(lease, "COMPLETED", "run-1", "session-1")).thenReturn(true);

        manager.processQueued("channel-1", "external-1");

        ArgumentCaptor<ChannelAgentChatPort.ChatCommand> command =
                ArgumentCaptor.forClass(ChannelAgentChatPort.ChatCommand.class);
        verify(chat).chat(command.capture());
        assertEquals(ChannelAgentChatPort.IdentityStatus.MAPPED, command.getValue().identityStatus());
        assertEquals(ChannelAgentChatPort.RuntimeAccess.AUTHENTICATED, command.getValue().runtimeAccess());
        assertEquals("user-1", command.getValue().identity().platformUserId());
    }

    @Test
    void staleIdentityIsNotTrustedAndIsAudited() {
        ChannelMessageRecord stored = queued();
        ChannelConversationLeaseService.LeaseHandle lease = lease();
        ChannelIdentityRecord identity = new ChannelIdentityRecord("mapping-1", "channel-1", "project-1",
                "sender-1", "user-1", "alice", ChannelStatus.ACTIVE, 1, "admin", null, null);
        when(repository.findInbound("channel-1", "external-1")).thenReturn(Optional.of(stored));
        when(leases.acquire(any(), any())).thenReturn(Optional.of(lease));
        when(protocol.restore(stored)).thenReturn(message());
        when(repository.findIdentity("channel-1", "sender-1")).thenReturn(Optional.of(identity));
        when(identities.isTrusted("project-1", identity)).thenReturn(false);
        when(leases.fail(lease, "CHANNEL_IDENTITY_NO_LONGER_AUTHORIZED")).thenReturn(true);

        manager.processQueued("channel-1", "external-1");

        verify(chat, never()).chat(any());
        verify(bindings, never()).resolve(any(), any());
        verify(leases).fail(lease, "CHANNEL_IDENTITY_NO_LONGER_AUTHORIZED");
        verify(audit).record(org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq("agent-1"), any(),
                org.mockito.ArgumentMatchers.eq("CHANNEL_IDENTITY_REJECTED"),
                org.mockito.ArgumentMatchers.eq("mapping-1"), org.mockito.ArgumentMatchers.eq("MEDIUM"),
                org.mockito.ArgumentMatchers.eq("BLOCKED"), any());
    }

    @Test
    void restoreFailureMarksClaimFailedAndAlwaysReleasesLease() {
        ChannelMessageRecord stored = queued();
        ChannelConversationLeaseService.LeaseHandle lease = lease();
        when(repository.findInbound("channel-1", "external-1")).thenReturn(Optional.of(stored));
        when(leases.acquire(any(), any())).thenReturn(Optional.of(lease));
        when(protocol.restore(stored)).thenThrow(new SecurityException("CHANNEL_INBOUND_ENVELOPE_IDENTITY_MISMATCH"));
        when(leases.fail(lease, "CHANNEL_INBOUND_ENVELOPE_IDENTITY_MISMATCH")).thenReturn(true);

        manager.processQueued("channel-1", "external-1");

        verify(leases).fail(lease, "CHANNEL_INBOUND_ENVELOPE_IDENTITY_MISMATCH");
        verify(leases).release(lease);
        verify(chat, never()).chat(any());
    }

    @Test
    void executorRejectionFailsDurableMessageAndPreservesOriginalFailure() {
        when(protocol.verifyAndProtect(any(), any(), any(), any()))
                .thenReturn(new ChannelInboundProtocolPort.VerifiedInbound("encrypted-envelope"));
        when(repository.insertInbound(any())).thenReturn(true);
        when(repository.resolveSession(any(), any(), any(), any(), any(), any())).thenReturn("session-1");
        doThrow(new RejectedExecutionException("saturated token=secret-value")).when(executor).execute(any());

        RejectedExecutionException failure = assertThrows(RejectedExecutionException.class,
                () -> manager.receive(receive()));

        assertEquals("saturated token=secret-value", failure.getMessage());
        verify(repository).markInboundFailed("channel-1", "external-1",
                "CHANNEL_EXECUTOR_REJECTED:saturated token=***");
        verify(audit).record(org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq("agent-1"), any(),
                org.mockito.ArgumentMatchers.eq("CHANNEL_MESSAGE_FAILED"),
                org.mockito.ArgumentMatchers.eq("external-1"), org.mockito.ArgumentMatchers.eq("LOW"),
                org.mockito.ArgumentMatchers.eq("FAILED"), any());
    }

    @Test
    void completionAuditFailureNeverRelabelsCommittedMessageFailed() {
        ChannelMessageRecord stored = queued();
        ChannelConversationLeaseService.LeaseHandle lease = lease();
        when(repository.findInbound("channel-1", "external-1")).thenReturn(Optional.of(stored));
        when(leases.acquire(any(), any())).thenReturn(Optional.of(lease));
        when(protocol.restore(stored)).thenReturn(message());
        when(bindings.resolve(any(), any()))
                .thenReturn(new ChannelExecutionBindingPort.ResolvedExecution(ExecutionType.WORKFLOW, "agent-1", 3, "agent-hash"));
        when(repository.findIdentity("channel-1", "sender-1")).thenReturn(Optional.empty());
        when(chat.chat(any())).thenReturn(new ChannelAgentChatPort.ChatResult("answer", "channel-user"));
        when(replies.enqueue(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ChannelReplyOutboxPort.ReplyDelivery(true, false));
        when(leases.complete(lease, "COMPLETED", "run-1", "session-1")).thenReturn(true);
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(
                any(), any(), any(), org.mockito.ArgumentMatchers.eq("CHANNEL_MESSAGE_COMPLETED"),
                any(), any(), any(), any());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> manager.processQueued("channel-1", "external-1"));

        assertEquals("CHANNEL_COMPLETION_AUDIT_FAILED_AFTER_COMMIT", failure.getMessage());
        verify(leases, never()).fail(any(), any());
        verify(leases).release(lease);
    }

    @Test
    void completionStateExceptionAfterReplyIsExplicitOutcomeUnknown() {
        ChannelMessageRecord stored = queued();
        ChannelConversationLeaseService.LeaseHandle lease = lease();
        when(repository.findInbound("channel-1", "external-1")).thenReturn(Optional.of(stored));
        when(leases.acquire(any(), any())).thenReturn(Optional.of(lease));
        when(protocol.restore(stored)).thenReturn(message());
        when(bindings.resolve(any(), any()))
                .thenReturn(new ChannelExecutionBindingPort.ResolvedExecution(ExecutionType.WORKFLOW, "agent-1", 3, "agent-hash"));
        when(repository.findIdentity("channel-1", "sender-1")).thenReturn(Optional.empty());
        when(chat.chat(any())).thenReturn(new ChannelAgentChatPort.ChatResult("answer", "channel-user"));
        when(replies.enqueue(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ChannelReplyOutboxPort.ReplyDelivery(false, false));
        when(leases.complete(lease, "REPLY_QUEUED", "run-1", "session-1"))
                .thenThrow(new IllegalStateException("repository unavailable"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> manager.processQueued("channel-1", "external-1"));

        assertEquals("CHANNEL_COMPLETION_STATE_UNKNOWN_AFTER_REPLY", failure.getMessage());
        verify(leases, never()).fail(any(), any());
        verify(leases).release(lease);
    }

    @Test
    void completionCasConflictIsQuarantinedWithoutFailingClaim() {
        ChannelMessageRecord stored = queued();
        ChannelConversationLeaseService.LeaseHandle lease = lease();
        when(repository.findInbound("channel-1", "external-1")).thenReturn(Optional.of(stored));
        when(leases.acquire(any(), any())).thenReturn(Optional.of(lease));
        when(protocol.restore(stored)).thenReturn(message());
        when(bindings.resolve(any(), any()))
                .thenReturn(new ChannelExecutionBindingPort.ResolvedExecution(ExecutionType.WORKFLOW, "agent-1", 3, "agent-hash"));
        when(repository.findIdentity("channel-1", "sender-1")).thenReturn(Optional.empty());
        when(chat.chat(any())).thenReturn(new ChannelAgentChatPort.ChatResult("answer", "channel-user"));
        when(replies.enqueue(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ChannelReplyOutboxPort.ReplyDelivery(false, false));
        when(leases.complete(lease, "REPLY_QUEUED", "run-1", "session-1")).thenReturn(false);

        manager.processQueued("channel-1", "external-1");

        verify(leases, never()).fail(any(), any());
        verify(audit).record(org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq(""), org.mockito.ArgumentMatchers.eq("channel-runtime"),
                org.mockito.ArgumentMatchers.eq("CHANNEL_INBOUND_RECOVERY_REQUIRED"),
                org.mockito.ArgumentMatchers.eq("external-1"), org.mockito.ArgumentMatchers.eq("MEDIUM"),
                org.mockito.ArgumentMatchers.eq("BLOCKED"), any());
    }

    @Test
    void pendingWorkerRenewsRecoversAndSchedulesDurableMessages() {
        ChannelConversationLeaseService.LeaseLost lost = new ChannelConversationLeaseService.LeaseLost(
                "project-1", "channel-1", "external-lost", "lock-lost");
        ChannelConversationLeasePort.RecoveredLease recovered = new ChannelConversationLeasePort.RecoveredLease(
                "project-1", "channel-1", "conversation-1", "sender-1", "external-recovery",
                "run-recovery", "session-recovery");
        when(leases.renewActive(any())).thenReturn(List.of(lost));
        when(leases.recoverExpired(20)).thenReturn(List.of(recovered));
        when(repository.findQueuedInbound(20)).thenReturn(List.of(queued()));

        manager.processPending();

        verify(audit).record(org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq(""), org.mockito.ArgumentMatchers.eq("channel-runtime"),
                org.mockito.ArgumentMatchers.eq("CHANNEL_INBOUND_LEASE_LOST"),
                org.mockito.ArgumentMatchers.eq("external-lost"), org.mockito.ArgumentMatchers.eq("MEDIUM"),
                org.mockito.ArgumentMatchers.eq("BLOCKED"), any());
        verify(audit).record(org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq(""), org.mockito.ArgumentMatchers.eq("channel-runtime"),
                org.mockito.ArgumentMatchers.eq("CHANNEL_INBOUND_RECOVERY_REQUIRED"),
                org.mockito.ArgumentMatchers.eq("external-recovery"), org.mockito.ArgumentMatchers.eq("MEDIUM"),
                org.mockito.ArgumentMatchers.eq("BLOCKED"), any());
        verify(executor).execute(any());
    }

    private ChannelModels.Receive receive() {
        return new ChannelModels.Receive("channel-1", message(), "100", "signature");
    }

    private ChannelModels.InboundMessage message() {
        return new ChannelModels.InboundMessage("external-1", "conversation-1", "sender-1",
                "hello", 100, Map.of(), "TEXT", java.util.List.of(), null);
    }

    private ChannelRecord channel() {
        return channelWithPolicy(ChannelAccessPolicy.OBSERVE_ONLY_UNKNOWN);
    }

    private ChannelRecord channelWithPolicy(ChannelAccessPolicy accessPolicy) {
        return new ChannelRecord("channel-1", "project-1",
                ExecutionBinding.workflow("agent-1", ExecutionVersionPolicy.LATEST_PUBLISHED, 3, "agent-hash"),
                "Oncall", "GENERIC_WEBHOOK", "credential-ref",
                Map.of("outboundUrl", "https://bridge.test/reply"), accessPolicy,
                ChannelStatus.ACTIVE, "admin", null, null);
    }

    private ChannelMessageRecord queued() {
        return new ChannelMessageRecord("message-1", "channel-1", "project-1", "external-1",
                "conversation-1", "sender-1", "session-1", "run-1", "INBOUND", "QUEUED",
                "encrypted-envelope", null, null, null);
    }

    private ChannelConversationLeaseService.LeaseHandle lease() {
        return new ChannelConversationLeaseService.LeaseHandle("project-1", "channel-1", "conversation-1",
                "sender-1", "external-1", "lock-1", Instant.now().plusSeconds(300));
    }
}
