package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.approval.ChannelApprovalActionRecord;
import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelInteractiveActionDispatcherTest {

    @Test
    void changePackagePartialMultiApprovalPreservesSharedCard() {
        OpsChannelApprovalActionService change = mock(OpsChannelApprovalActionService.class);
        OpsWorkflowApprovalActionService workflow = mock(OpsWorkflowApprovalActionService.class);
        OpsChannelInteractiveActionDispatcher dispatcher = new OpsChannelInteractiveActionDispatcher(change, workflow);
        var changeOutcome = new OpsChannelApprovalActionService.ActionOutcome(
                true, ChannelApprovalActionRecord.Decision.APPROVE, "REVIEWING", 3, "hash-3", 2, 1);
        when(change.tryExecute(any(), any())).thenReturn(Optional.of(changeOutcome));

        var result = dispatcher.tryExecute("channel-1", envelope());

        assertTrue(result.isPresent());
        assertFalse(result.get().terminal());
        assertEquals("Approval recorded (1/2)", result.get().presentation());
        verify(workflow, never()).tryExecute(any(), any());
    }

    @Test
    void terminalWorkflowDecisionClosesProviderCard() {
        OpsChannelApprovalActionService change = mock(OpsChannelApprovalActionService.class);
        OpsWorkflowApprovalActionService workflow = mock(OpsWorkflowApprovalActionService.class);
        OpsChannelInteractiveActionDispatcher dispatcher = new OpsChannelInteractiveActionDispatcher(change, workflow);
        when(change.tryExecute(any(), any())).thenReturn(Optional.empty());
        when(workflow.tryExecute(any(), any())).thenReturn(Optional.of(
                new OpsWorkflowApprovalActionService.ActionOutcome(
                        "workflow-approval-1", "run-1", "project-1", "approval-node",
                        WorkflowApprovalRecord.Decision.REJECT, "REJECTED")));

        var result = dispatcher.tryExecute("channel-1", envelope());

        assertTrue(result.isPresent());
        assertTrue(result.get().terminal());
        assertEquals("Workflow rejected in OrbisOps", result.get().presentation());
        assertEquals("WORKFLOW_APPROVAL", result.get().handler());
    }

    private ChannelInteractiveActionEnvelope envelope() {
        return new ChannelInteractiveActionEnvelope(
                new ChannelInteractiveAction("action", "Approve", "opaque-action-key",
                        ChannelInteractiveAction.ActionStyle.PRIMARY),
                new ChannelExternalPrincipal("external-user", "Alice", ChannelExternalPrincipal.PrincipalKind.USER),
                new ChannelMessageRef("message-1",
                        new ChannelConversationRef("room-1", ChannelConversationRef.ConversationKind.GROUP)),
                Instant.parse("2026-08-16T00:00:00Z"),
                "message-1:action");
    }
}
