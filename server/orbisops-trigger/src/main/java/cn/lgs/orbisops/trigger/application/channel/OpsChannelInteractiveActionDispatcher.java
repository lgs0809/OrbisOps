package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.approval.ChannelApprovalActionRecord;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRecord;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Provider-neutral command surface dispatcher. Business authority remains in each domain-specific handler. */
@Component
public class OpsChannelInteractiveActionDispatcher {

    private final OpsChannelApprovalActionService changePackageApprovals;
    private final OpsWorkflowApprovalActionService workflowApprovals;

    public OpsChannelInteractiveActionDispatcher(OpsChannelApprovalActionService changePackageApprovals,
                                                 OpsWorkflowApprovalActionService workflowApprovals) {
        if (changePackageApprovals == null) throw new IllegalArgumentException("CHANNEL_CHANGE_APPROVAL_HANDLER_REQUIRED");
        if (workflowApprovals == null) throw new IllegalArgumentException("CHANNEL_WORKFLOW_APPROVAL_HANDLER_REQUIRED");
        this.changePackageApprovals = changePackageApprovals;
        this.workflowApprovals = workflowApprovals;
    }

    public Optional<ActionResult> tryExecute(String channelId, ChannelInteractiveActionEnvelope envelope) {
        Optional<OpsChannelApprovalActionService.ActionOutcome> change =
                changePackageApprovals.tryExecute(channelId, envelope);
        if (change.isPresent()) {
            OpsChannelApprovalActionService.ActionOutcome outcome = change.get();
            boolean terminal = !outcome.waitingForMoreApprovals();
            String presentation = outcome.waitingForMoreApprovals()
                    ? "Approval recorded (" + outcome.approvedCount() + "/" + outcome.requiredApprovals() + ")"
                    : outcome.decision() == ChannelApprovalActionRecord.Decision.APPROVE
                    ? "Approved in OrbisOps"
                    : "Rejected in OrbisOps";
            return Optional.of(new ActionResult(
                    "CHANGE_PACKAGE",
                    terminal,
                    presentation,
                    outcome.status()));
        }

        Optional<OpsWorkflowApprovalActionService.ActionOutcome> workflow =
                workflowApprovals.tryExecute(channelId, envelope);
        if (workflow.isPresent()) {
            OpsWorkflowApprovalActionService.ActionOutcome outcome = workflow.get();
            String presentation = outcome.decision() == WorkflowApprovalRecord.Decision.APPROVE
                    ? "Workflow approved in OrbisOps"
                    : "Workflow rejected in OrbisOps";
            return Optional.of(new ActionResult(
                    "WORKFLOW_APPROVAL",
                    true,
                    presentation,
                    outcome.status()));
        }
        return Optional.empty();
    }

    public record ActionResult(String handler,
                               boolean terminal,
                               String presentation,
                               String status) {
    }
}
