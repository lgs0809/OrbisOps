package cn.lgs.orbisops.application.runtime.workflow;

import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowResourceSnapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record WorkflowBoundToolInvocation(
        String projectId,
        String actor,
        String sessionId,
        String runId,
        String nodeId,
        int attempt,
        int toolCallIndex,
        String idempotencyKey,
        String toolsetId,
        String toolName,
        BoundWorkflowResourceSnapshot boundResource,
        Map<String, Object> arguments,
        boolean readOnly,
        boolean changePackageProposal
) {

    public WorkflowBoundToolInvocation {
        projectId = required(projectId, "WORKFLOW_TOOL_PROJECT_ID_REQUIRED");
        actor = required(actor, "WORKFLOW_TOOL_ACTOR_REQUIRED");
        sessionId = required(sessionId, "WORKFLOW_TOOL_SESSION_ID_REQUIRED");
        runId = required(runId, "WORKFLOW_TOOL_RUN_ID_REQUIRED");
        nodeId = required(nodeId, "WORKFLOW_TOOL_NODE_ID_REQUIRED");
        if (attempt <= 0 || toolCallIndex < 0) {
            throw new IllegalArgumentException("WORKFLOW_TOOL_ATTEMPT_INVALID");
        }
        idempotencyKey = required(idempotencyKey, "WORKFLOW_TOOL_IDEMPOTENCY_KEY_REQUIRED");
        String expectedKey = runId + ":" + nodeId + ":" + attempt + ":" + toolCallIndex;
        if (!expectedKey.equals(idempotencyKey)) {
            throw new IllegalArgumentException("WORKFLOW_TOOL_IDEMPOTENCY_KEY_MISMATCH");
        }
        toolsetId = required(toolsetId, "WORKFLOW_TOOLSET_ID_REQUIRED");
        toolName = required(toolName, "WORKFLOW_TOOL_NAME_REQUIRED");
        if (boundResource == null) {
            throw new IllegalArgumentException("WORKFLOW_BOUND_TOOL_RESOURCE_REQUIRED");
        }
        arguments = arguments == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        if (!readOnly && !changePackageProposal) {
            throw new SecurityException("WORKFLOW_TOOL_WRITE_REQUIRES_CHANGE_PACKAGE_PROPOSAL");
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
