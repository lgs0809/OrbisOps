package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelIdentityDirectoryPort;
import cn.lgs.orbisops.application.channel.ChannelRuntimeAuditPort;
import cn.lgs.orbisops.application.channel.ChannelRuntimeReadPort;
import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalApplicationService;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRecord;
import cn.lgs.orbisops.application.worksession.run.WorkSessionRunApplicationService;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsWorkflowApprovalActionServiceTest {

    private WorkflowApprovalApplicationService approvals;
    private ChannelRuntimeReadPort channels;
    private ChannelIdentityDirectoryPort identities;
    private WorkSessionRunApplicationService workSessions;
    private ApplicationEventPublisher events;
    private ChannelRuntimeAuditPort audit;
    private OpsWorkflowApprovalActionService service;

    @BeforeEach
    void setUp() {
        approvals = mock(WorkflowApprovalApplicationService.class);
        channels = mock(ChannelRuntimeReadPort.class);
        identities = mock(ChannelIdentityDirectoryPort.class);
        workSessions = mock(WorkSessionRunApplicationService.class);
        events = mock(ApplicationEventPublisher.class);
        audit = mock(ChannelRuntimeAuditPort.class);
        service = new OpsWorkflowApprovalActionService(approvals, channels, identities, workSessions, events, audit);
    }

    @Test
    void authorizedChannelActorCommitsDecisionAndPublishesResumeEventOnly() {
        WorkflowApprovalRecord waiting = approval(WorkflowApprovalRecord.Status.WAITING, "");
        WorkflowApprovalRecord approved = approval(WorkflowApprovalRecord.Status.APPROVED, "user-1");
        var resolved = new WorkflowApprovalApplicationService.ResolvedAction(
                waiting, WorkflowApprovalRecord.Decision.APPROVE);
        ChannelIdentityRecord identity = identity();
        when(approvals.resolveAction("opaque-workflow-action")).thenReturn(resolved);
        when(channels.findIdentity("channel-1", "external-user")).thenReturn(Optional.of(identity));
        when(identities.isTrusted("project-1", identity)).thenReturn(true);
        when(workSessions.resumeApproval("run-1", "project-1", "user-1"))
                .thenReturn(mock(WorkSessionRunSnapshot.class));
        when(approvals.decide(resolved, "user-1")).thenReturn(approved);

        var result = service.tryExecute("channel-1", envelope("room-1"));

        assertTrue(result.isPresent());
        assertEquals(WorkflowApprovalRecord.Decision.APPROVE, result.get().decision());
        verify(workSessions).resumeApproval("run-1", "project-1", "user-1");
        verify(approvals).decide(resolved, "user-1");
        ArgumentCaptor<WorkflowApprovalDecidedEvent> event = ArgumentCaptor.forClass(WorkflowApprovalDecidedEvent.class);
        verify(events).publishEvent(event.capture());
        assertEquals("run-1", event.getValue().runId());
        assertEquals("user-1", event.getValue().actor());
    }

    @Test
    void wrongConversationCannotConsumeDecision() {
        WorkflowApprovalRecord waiting = approval(WorkflowApprovalRecord.Status.WAITING, "");
        when(approvals.resolveAction("opaque-workflow-action")).thenReturn(
                new WorkflowApprovalApplicationService.ResolvedAction(waiting, WorkflowApprovalRecord.Decision.APPROVE));

        SecurityException failure = assertThrows(SecurityException.class,
                () -> service.tryExecute("channel-1", envelope("other-room")));

        assertEquals("WORKFLOW_APPROVAL_ACTION_CONVERSATION_MISMATCH", failure.getMessage());
        verify(channels, never()).findIdentity(any(), any());
        verify(approvals, never()).decide(any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void unmappedOrRemovedIdentityCannotConsumeDecision() {
        WorkflowApprovalRecord waiting = approval(WorkflowApprovalRecord.Status.WAITING, "");
        var resolved = new WorkflowApprovalApplicationService.ResolvedAction(
                waiting, WorkflowApprovalRecord.Decision.REJECT);
        when(approvals.resolveAction("opaque-workflow-action")).thenReturn(resolved);
        when(channels.findIdentity("channel-1", "external-user")).thenReturn(Optional.empty());

        SecurityException failure = assertThrows(SecurityException.class,
                () -> service.tryExecute("channel-1", envelope("room-1")));

        assertEquals("WORKFLOW_APPROVAL_IDENTITY_MAPPING_REQUIRED", failure.getMessage());
        verify(workSessions, never()).resumeApproval(any(), any(), any());
        verify(approvals, never()).decide(any(), any());
    }

    @Test
    void actorLosingWorkSessionWritePermissionAtClickTimeCannotConsumeDecision() {
        WorkflowApprovalRecord waiting = approval(WorkflowApprovalRecord.Status.WAITING, "");
        var resolved = new WorkflowApprovalApplicationService.ResolvedAction(
                waiting, WorkflowApprovalRecord.Decision.APPROVE);
        ChannelIdentityRecord identity = identity();
        when(approvals.resolveAction("opaque-workflow-action")).thenReturn(resolved);
        when(channels.findIdentity("channel-1", "external-user")).thenReturn(Optional.of(identity));
        when(identities.isTrusted("project-1", identity)).thenReturn(true);
        doThrow(new SecurityException("WORK_SESSION_APPROVAL_FORBIDDEN"))
                .when(workSessions).resumeApproval("run-1", "project-1", "user-1");

        SecurityException failure = assertThrows(SecurityException.class,
                () -> service.tryExecute("channel-1", envelope("room-1")));

        assertEquals("WORK_SESSION_APPROVAL_FORBIDDEN", failure.getMessage());
        verify(approvals, never()).decide(any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void nonWorkflowOpaqueActionFallsThroughToOtherInteractiveHandlers() {
        when(approvals.resolveAction("ordinary-action-key"))
                .thenThrow(new SecurityException("WORKFLOW_APPROVAL_ACTION_INVALID"));

        Optional<OpsWorkflowApprovalActionService.ActionOutcome> result =
                service.tryExecute("channel-1", envelopeWithAction("room-1", "ordinary-action-key"));

        assertTrue(result.isEmpty());
        verify(channels, never()).findIdentity(any(), any());
    }

    private ChannelInteractiveActionEnvelope envelope(String conversationId) {
        return envelopeWithAction(conversationId, "opaque-workflow-action");
    }

    private ChannelInteractiveActionEnvelope envelopeWithAction(String conversationId, String actionKey) {
        ChannelConversationRef conversation = new ChannelConversationRef(
                conversationId, ChannelConversationRef.ConversationKind.GROUP);
        return new ChannelInteractiveActionEnvelope(
                new ChannelInteractiveAction("workflow-action", "Approve", actionKey,
                        ChannelInteractiveAction.ActionStyle.PRIMARY),
                new ChannelExternalPrincipal("external-user", "Alice", ChannelExternalPrincipal.PrincipalKind.USER),
                new ChannelMessageRef("message-1", conversation),
                Instant.parse("2026-08-16T00:00:10Z"),
                "message-1:workflow-action");
    }

    private ChannelIdentityRecord identity() {
        return new ChannelIdentityRecord(
                "mapping-1", "channel-1", "project-1", "external-user",
                "user-1", "alice", ChannelStatus.ACTIVE, 1, "admin", null, null);
    }

    private WorkflowApprovalRecord approval(WorkflowApprovalRecord.Status status, String decidedBy) {
        Instant requestedAt = Instant.parse("2026-08-16T00:00:00Z");
        return new WorkflowApprovalRecord(
                "workflow-approval-1", "run-1", "project-1", "approval-node",
                "wait-hash", "approve-hash", "reject-hash", status,
                "channel-1", "room-1", "Approve production rollout",
                requestedAt, requestedAt.plusSeconds(1800),
                decidedBy,
                status == WorkflowApprovalRecord.Status.WAITING ? null : requestedAt.plusSeconds(10));
    }
}
