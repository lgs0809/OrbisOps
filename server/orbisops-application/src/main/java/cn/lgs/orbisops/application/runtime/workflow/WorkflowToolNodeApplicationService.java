package cn.lgs.orbisops.application.runtime.workflow;

import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeStatus;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunState;
import cn.lgs.orbisops.domain.runtime.workflow.service.DurableWorkflowRuntimePolicy;

import java.util.Map;

/** Executes a durable Workflow Tool node through the unified Tool Execution outbound port. */
public final class WorkflowToolNodeApplicationService {

    private final WorkflowBoundToolExecutionPort executionPort;
    private final DurableWorkflowRuntimePolicy runtimePolicy;

    public WorkflowToolNodeApplicationService(
            WorkflowBoundToolExecutionPort executionPort) {
        this(executionPort, new DurableWorkflowRuntimePolicy());
    }

    WorkflowToolNodeApplicationService(
            WorkflowBoundToolExecutionPort executionPort,
            DurableWorkflowRuntimePolicy runtimePolicy) {
        if (executionPort == null || runtimePolicy == null) {
            throw new IllegalArgumentException("WORKFLOW_TOOL_NODE_DEPENDENCY_REQUIRED");
        }
        this.executionPort = executionPort;
        this.runtimePolicy = runtimePolicy;
    }

    public WorkflowBoundToolResult execute(
            DurableWorkflowRunState state,
            String actor,
            String sessionId,
            String nodeId,
            int toolCallIndex,
            String toolsetId,
            String toolName,
            BoundWorkflowResourceSnapshot boundResource,
            Map<String, Object> arguments,
            boolean readOnly,
            boolean changePackageProposal) {
        if (state == null) throw new IllegalArgumentException("WORKFLOW_RUN_STATE_REQUIRED");
        DurableWorkflowNodeState node = state.node(nodeId);
        if (node.status() != DurableWorkflowNodeStatus.RUNNING) {
            throw new IllegalStateException("WORKFLOW_TOOL_NODE_NOT_RUNNING:" + nodeId);
        }
        String idempotencyKey = runtimePolicy.toolIdempotencyKey(
                state, nodeId, toolCallIndex);
        return executionPort.execute(new WorkflowBoundToolInvocation(
                state.projectId(), actor, sessionId, state.runId(), nodeId,
                node.attempt(), toolCallIndex, idempotencyKey,
                toolsetId, toolName, boundResource, arguments,
                readOnly, changePackageProposal));
    }
}
