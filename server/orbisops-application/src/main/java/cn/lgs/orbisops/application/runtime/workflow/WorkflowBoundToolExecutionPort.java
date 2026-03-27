package cn.lgs.orbisops.application.runtime.workflow;

@FunctionalInterface
public interface WorkflowBoundToolExecutionPort {
    WorkflowBoundToolResult execute(WorkflowBoundToolInvocation invocation);
}
