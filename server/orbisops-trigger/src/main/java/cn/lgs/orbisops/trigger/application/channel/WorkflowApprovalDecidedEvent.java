package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRecord;

public record WorkflowApprovalDecidedEvent(
        String approvalId,
        String runId,
        String projectId,
        String nodeId,
        String actor,
        WorkflowApprovalRecord.Decision decision) {
}
