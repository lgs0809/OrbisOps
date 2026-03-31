package cn.lgs.orbisops.application.toolexecution;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResult;
import cn.lgs.orbisops.domain.toolexecution.model.ToolInvocation;
import cn.lgs.orbisops.domain.toolexecution.model.ToolInvocationResult;

/**
 * Provider-neutral facade over the existing authoritative Tool execution service.
 * Policy, idempotency, audit, receipt recording and completion reconciliation remain single-sourced.
 */
public final class ToolInvocationCoordinator implements ToolInvocationPort {

    private final ToolExecutionApplicationService execution;

    public ToolInvocationCoordinator(ToolExecutionApplicationService execution) {
        if (execution == null) throw new IllegalArgumentException("TOOL_EXECUTION_APPLICATION_REQUIRED");
        this.execution = execution;
    }

    @Override
    public ToolInvocationResult invoke(ToolInvocation invocation) {
        if (invocation == null) throw new IllegalArgumentException("TOOL_INVOCATION_REQUIRED");
        ToolExecutionResult result = execution.execute(invocation);
        return ToolInvocationResult.from(result, invocation.context().scope());
    }
}
