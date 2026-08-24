package cn.lgs.orbisops.trigger.application.worksession;

import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalApplicationService;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRecord;
import cn.lgs.orbisops.application.worksession.run.WorkSessionRunApplicationService;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunSnapshot;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStatus;
import cn.lgs.orbisops.trigger.application.channel.WorkflowApprovalDecidedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsWorkflowApprovalWebApplicationServiceTest {

    private WorkflowApprovalApplicationService approvals;
    private WorkSessionRunApplicationService workSessions;
    private ApplicationEventPublisher events;
    private OpsWorkflowApprovalWebApplicationService service;

    @BeforeEach
    void setUp() {
        approvals = mock(WorkflowApprovalApplicationService.class);
        workSessions = mock(WorkSessionRunApplicationService.class);
        events = mock(ApplicationEventPublisher.class);
        service = new OpsWorkflowApprovalWebApplicationService(approvals, workSessions, events);
    }

    @Test
    void staleBrowserApprovalCannotApproveTheNextNodeOrPublishResume() {
        when(approvals.findCurrent("run-1")).thenReturn(Optional.of(approval(WorkflowApprovalRecord.Status.WAITING, "")));
        SecurityException failure = assertThrows(SecurityException.class,
                () -> service.decide("run-1", "project-1", "user-1", "APPROVE", "obsolete-approval"));
        assertEquals("WORKFLOW_APPROVAL_REVIEWED_RECORD_MISMATCH", failure.getMessage());
        org.mockito.Mockito.verifyNoInteractions(workSessions, events);
        verify(approvals, never()).decide(any(), any(), any());
        assertThrows(IllegalArgumentException.class,
                () -> service.decide("run-1", "project-1", "user-1", "APPROVE", ""));
    }

    @Test
    void staleResumeCannotTargetANewerApproval() {
        when(approvals.findCurrent("run-1")).thenReturn(Optional.of(approval(WorkflowApprovalRecord.Status.APPROVED, "user-1")));
        assertThrows(SecurityException.class,
                () -> service.retryResume("run-1", "project-1", "user-1", "obsolete-approval"));
        org.mockito.Mockito.verifyNoInteractions(workSessions, events);
    }

    @Test
    void actorViewRequiresWorkSessionReadAndNeverExposesAuthorityHashes() {
        WorkSessionRunSnapshot run = run(WorkSessionRunStatus.WAITING_APPROVAL);
        WorkflowApprovalRecord waiting = approval(WorkflowApprovalRecord.Status.WAITING, "");
        when(workSessions.get("run-1", "project-1")).thenReturn(run);
        when(workSessions.resumeApproval("run-1", "project-1", "user-1")).thenReturn(run);
        when(approvals.findCurrent("run-1")).thenReturn(Optional.of(waiting));

        Map<String, Object> view = service.viewForActor("run-1", "project-1", "user-1");

        verify(workSessions).assertActorCanRead("run-1", "project-1", "user-1");
        assertEquals(true, view.get("available"));
        assertEquals(true, view.get("canDecide"));
        assertEquals("Approve production rollout", view.get("requestSummary"));
        assertFalse(view.containsKey("waitTokenHash"));
        assertFalse(view.containsKey("approveActionHash"));
        assertFalse(view.containsKey("rejectActionHash"));
        assertFalse(view.containsKey("channelId"));
        assertFalse(view.containsKey("target"));
    }

    @Test
    void waitingDecisionCommitsOnceAndPublishesSameResumeEventAsChannel() {
        WorkSessionRunSnapshot run = run(WorkSessionRunStatus.WAITING_APPROVAL);
        WorkflowApprovalRecord waiting = approval(WorkflowApprovalRecord.Status.WAITING, "");
        WorkflowApprovalRecord approved = approval(WorkflowApprovalRecord.Status.APPROVED, "user-1");
        when(approvals.findCurrent("run-1")).thenReturn(Optional.of(waiting));
        when(workSessions.resumeApproval("run-1", "project-1", "user-1")).thenReturn(run);
        when(approvals.decide(waiting, WorkflowApprovalRecord.Decision.APPROVE, "user-1")).thenReturn(approved);

        Map<String, Object> view = service.decide("run-1", "project-1", "user-1", "approve", "workflow-approval-1");

        assertEquals("APPROVED", view.get("status"));
        assertEquals(true, view.get("resumeRequired"));
        ArgumentCaptor<WorkflowApprovalDecidedEvent> event = ArgumentCaptor.forClass(WorkflowApprovalDecidedEvent.class);
        verify(events).publishEvent(event.capture());
        assertEquals(WorkflowApprovalRecord.Decision.APPROVE, event.getValue().decision());
        assertEquals("user-1", event.getValue().actor());
    }

    @Test
    void terminalSameDecisionCanRepublishResumeWithoutRewritingLedger() {
        WorkSessionRunSnapshot run = run(WorkSessionRunStatus.WAITING_APPROVAL);
        WorkflowApprovalRecord approved = approval(WorkflowApprovalRecord.Status.APPROVED, "user-1");
        when(approvals.findCurrent("run-1")).thenReturn(Optional.of(approved));
        when(workSessions.resumeApproval("run-1", "project-1", "user-1")).thenReturn(run);

        Map<String, Object> view = service.decide("run-1", "project-1", "user-1", "APPROVE", "workflow-approval-1");

        assertEquals("APPROVED", view.get("status"));
        verify(approvals, never()).decide(any(), any(), any());
        verify(events).publishEvent(any(WorkflowApprovalDecidedEvent.class));
    }

    @Test
    void oppositeDecisionAfterTerminalDecisionIsForbidden() {
        WorkSessionRunSnapshot run = run(WorkSessionRunStatus.WAITING_APPROVAL);
        WorkflowApprovalRecord approved = approval(WorkflowApprovalRecord.Status.APPROVED, "user-1");
        when(approvals.findCurrent("run-1")).thenReturn(Optional.of(approved));
        when(workSessions.resumeApproval("run-1", "project-1", "user-1")).thenReturn(run);

        SecurityException failure = assertThrows(SecurityException.class,
                () -> service.decide("run-1", "project-1", "user-1", "REJECT", "workflow-approval-1"));

        assertEquals("WORKFLOW_APPROVAL_OPPOSITE_DECISION_FORBIDDEN", failure.getMessage());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void explicitRetryResumeRequiresTerminalDecisionAndCurrentWorkSessionWriteAuthority() {
        WorkSessionRunSnapshot run = run(WorkSessionRunStatus.WAITING_APPROVAL);
        WorkflowApprovalRecord rejected = approval(WorkflowApprovalRecord.Status.REJECTED, "user-1");
        when(approvals.findCurrent("run-1")).thenReturn(Optional.of(rejected));
        when(workSessions.resumeApproval("run-1", "project-1", "user-1")).thenReturn(run);

        Map<String, Object> view = service.retryResume("run-1", "project-1", "user-1", "workflow-approval-1");

        assertEquals("REJECTED", view.get("status"));
        verify(events).publishEvent(any(WorkflowApprovalDecidedEvent.class));
        verify(approvals, never()).decide(any(), any(), any());
    }

    @Test
    void noApprovalReturnsExplicitUnavailableReadModel() {
        WorkSessionRunSnapshot run = run(WorkSessionRunStatus.RUNNING);
        when(workSessions.get("run-1", "project-1")).thenReturn(run);
        when(approvals.findCurrent("run-1")).thenReturn(Optional.empty());

        Map<String, Object> view = service.view("run-1", "project-1");

        assertEquals(false, view.get("available"));
        assertEquals("RUNNING", view.get("runStatus"));
        assertEquals(4, view.size());
    }

    private WorkSessionRunSnapshot run(WorkSessionRunStatus status) {
        WorkSessionRunSnapshot run = mock(WorkSessionRunSnapshot.class);
        when(run.runId()).thenReturn("run-1");
        when(run.projectId()).thenReturn("project-1");
        when(run.status()).thenReturn(status);
        return run;
    }

    private WorkflowApprovalRecord approval(WorkflowApprovalRecord.Status status, String decidedBy) {
        Instant requestedAt = Instant.parse("2026-08-16T00:00:00Z");
        return new WorkflowApprovalRecord(
                "workflow-approval-1", "run-1", "project-1", "approval-node",
                "wait-hash", "approve-hash", "reject-hash", status,
                "channel-1", "room-1", "Approve production rollout",
                requestedAt, requestedAt.plusSeconds(1800), decidedBy,
                status == WorkflowApprovalRecord.Status.WAITING ? null : requestedAt.plusSeconds(10));
    }
}
