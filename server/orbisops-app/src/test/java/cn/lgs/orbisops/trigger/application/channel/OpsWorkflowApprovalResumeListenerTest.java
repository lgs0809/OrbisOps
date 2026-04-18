package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelRuntimeAuditPort;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRecord;
import cn.lgs.orbisops.trigger.application.worksession.OpsWorkSessionRunApplicationFacade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsWorkflowApprovalResumeListenerTest {

    @Test
    void committedDecisionSchedulesFreshApprovalResumeAttempt() {
        OpsWorkSessionRunApplicationFacade workSessions = mock(OpsWorkSessionRunApplicationFacade.class);
        ChannelRuntimeAuditPort audit = mock(ChannelRuntimeAuditPort.class);
        OpsWorkflowApprovalResumeListener listener = new OpsWorkflowApprovalResumeListener(workSessions, audit);
        WorkflowApprovalDecidedEvent event = event();

        listener.onDecision(event);

        verify(workSessions).resumeApproval("run-1", "project-1", "user-1");
        verify(audit).record(
                org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq("run-1"),
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq("WORKFLOW_APPROVAL_RESUME_SCHEDULED"),
                org.mockito.ArgumentMatchers.eq("workflow-approval-1"),
                org.mockito.ArgumentMatchers.eq("MEDIUM"),
                org.mockito.ArgumentMatchers.eq("SUCCEEDED"), any());
    }

    @Test
    void schedulingFailureIsAuditedButDoesNotThrowBackIntoCommittedDecision() {
        OpsWorkSessionRunApplicationFacade workSessions = mock(OpsWorkSessionRunApplicationFacade.class);
        ChannelRuntimeAuditPort audit = mock(ChannelRuntimeAuditPort.class);
        OpsWorkflowApprovalResumeListener listener = new OpsWorkflowApprovalResumeListener(workSessions, audit);
        WorkflowApprovalDecidedEvent event = event();
        doThrow(new IllegalStateException("scheduler unavailable"))
                .when(workSessions).resumeApproval("run-1", "project-1", "user-1");

        assertDoesNotThrow(() -> listener.onDecision(event));

        verify(audit).record(
                org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq("run-1"),
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq("WORKFLOW_APPROVAL_RESUME_SCHEDULE_FAILED"),
                org.mockito.ArgumentMatchers.eq("workflow-approval-1"),
                org.mockito.ArgumentMatchers.eq("MEDIUM"),
                org.mockito.ArgumentMatchers.eq("FAILED"), any());
    }

    private WorkflowApprovalDecidedEvent event() {
        return new WorkflowApprovalDecidedEvent(
                "workflow-approval-1", "run-1", "project-1", "approval-node", "user-1",
                WorkflowApprovalRecord.Decision.APPROVE);
    }
}
