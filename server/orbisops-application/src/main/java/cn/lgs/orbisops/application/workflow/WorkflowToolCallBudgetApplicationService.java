package cn.lgs.orbisops.application.workflow;

import cn.lgs.orbisops.domain.runtime.workflow.adapter.repository.IWorkflowToolCallBudgetRepository;

public final class WorkflowToolCallBudgetApplicationService {
    private final IWorkflowToolCallBudgetRepository repository;

    public WorkflowToolCallBudgetApplicationService(IWorkflowToolCallBudgetRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    public int reserve(IWorkflowToolCallBudgetRepository.Dispatch dispatch) {
        if (dispatch.runId() == null || dispatch.runId().isBlank()) return 0; // Non-workflow legacy caller.
        if (dispatch.projectId() == null || dispatch.projectId().isBlank()
                || dispatch.logicalCallId() == null || dispatch.logicalCallId().isBlank()
                || dispatch.physicalAttempt() < 1 || dispatch.physicalAttempt() > 2) {
            throw new SecurityException("WORKFLOW_TOOL_DISPATCH_IDENTITY_INVALID");
        }
        return repository.reserve(dispatch);
    }
}
