package cn.lgs.orbisops.domain.runtime.workflow.adapter.repository;

/** Durable reservation immediately before a physical tools/call dispatch, including retries. */
public interface IWorkflowToolCallBudgetRepository {
    int reserve(Dispatch dispatch);

    record Dispatch(String projectId, String runId, String nodeId, String mcpId, String toolName,
                    String logicalCallId, int physicalAttempt, String requestId) { }
}
