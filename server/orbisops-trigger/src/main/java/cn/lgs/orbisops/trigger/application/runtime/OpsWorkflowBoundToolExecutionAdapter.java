package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.runtime.workflow.WorkflowBoundToolExecutionPort;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowBoundToolInvocation;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowBoundToolResult;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionApplicationService;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResponse;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Workflow Tool Node ACL into the same unified Tool Execution chain used by Skill Replay. */
@Component
public final class OpsWorkflowBoundToolExecutionAdapter
        implements WorkflowBoundToolExecutionPort {

    private final ToolExecutionApplicationService toolExecution;

    public OpsWorkflowBoundToolExecutionAdapter(
            ToolExecutionApplicationService toolExecution) {
        if (toolExecution == null) {
            throw new IllegalArgumentException("WORKFLOW_TOOL_EXECUTION_REQUIRED");
        }
        this.toolExecution = toolExecution;
    }

    @Override
    public WorkflowBoundToolResult execute(
            WorkflowBoundToolInvocation invocation) {
        if (invocation == null) {
            throw new IllegalArgumentException("WORKFLOW_BOUND_TOOL_INVOCATION_REQUIRED");
        }
        String snapshot = invocation.boundResource().toString();
        ToolExecutionResponse response = toolExecution.execute(new ToolExecutionRequest(
                invocation.projectId(),
                "",
                invocation.actor(),
                invocation.toolsetId(),
                invocation.toolName(),
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW,
                invocation.arguments(),
                invocation.sessionId(),
                invocation.runId(),
                Map.of(
                        "workflowTool", true,
                        "workflowNodeId", invocation.nodeId(),
                        "workflowAttempt", invocation.attempt(),
                        "workflowToolCallIndex", invocation.toolCallIndex(),
                        "idempotencyKey", invocation.idempotencyKey(),
                        "boundResourceSnapshot", snapshot,
                        "boundResourceSnapshotHash", CanonicalObjectHasher.sha256(snapshot),
                        "readOnly", invocation.readOnly(),
                        "changePackageProposal", invocation.changePackageProposal()),
                Map.of()));
        return new WorkflowBoundToolResult(
                response.recorded().resultId(),
                response.recorded().evidenceId(),
                response.recorded().outputHash(),
                response.allowed(),
                response.payload());
    }
}
