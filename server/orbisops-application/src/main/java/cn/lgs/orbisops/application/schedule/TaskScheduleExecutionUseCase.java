package cn.lgs.orbisops.application.schedule;

import java.util.List;

/** Application process manager for manual schedule execution and execution history query. */
public final class TaskScheduleExecutionUseCase {

    private final TaskScheduleCatalogPort catalogPort;
    private final TaskScheduleExecutionPort executionPort;
    private final TaskExecutionCatalogPort executionCatalogPort;

    public TaskScheduleExecutionUseCase(
            TaskScheduleCatalogPort catalogPort,
            TaskScheduleExecutionPort executionPort,
            TaskExecutionCatalogPort executionCatalogPort) {
        if (catalogPort == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_CATALOG_PORT_REQUIRED");
        }
        if (executionPort == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_EXECUTION_PORT_REQUIRED");
        }
        if (executionCatalogPort == null) {
            throw new IllegalArgumentException("TASK_EXECUTION_CATALOG_PORT_REQUIRED");
        }
        this.catalogPort = catalogPort;
        this.executionPort = executionPort;
        this.executionCatalogPort = executionCatalogPort;
    }

    public Long runNow(Long id, String projectId) {
        if (id == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        TaskScheduleDefinition schedule = requireSchedule(id);
        assertProject(schedule, projectId);
        return executionPort.submit(schedule, "MANUAL");
    }

    /** Trusted scheduler entry; task ownership is resolved from the authoritative catalog by id. */
    public Long runScheduled(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        return executionPort.submit(requireSchedule(id), "SCHEDULED");
    }

    public List<TaskExecutionView> listExecutions(String projectId, Long scheduleId, Integer limit) {
        if (scheduleId == null) {
            throw new IllegalArgumentException("查询巡检记录必须提供 scheduleId");
        }
        TaskScheduleDefinition schedule = requireSchedule(scheduleId);
        assertProject(schedule, projectId);
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        List<TaskExecutionView> executions = executionCatalogPort.list(scheduleId, safeLimit);
        return executions == null || executions.isEmpty() ? List.of() : List.copyOf(executions);
    }

    private TaskScheduleDefinition requireSchedule(Long id) {
        TaskScheduleDefinition schedule = catalogPort.findById(id);
        if (schedule == null) {
            throw new IllegalArgumentException("任务不存在：" + id);
        }
        return schedule;
    }

    private void assertProject(TaskScheduleDefinition schedule, String projectId) {
        if (!hasText(projectId)) {
            throw new IllegalArgumentException("操作巡检任务必须提供 projectId");
        }
        String requestedProjectId = projectId.trim();
        if (!requestedProjectId.equals(schedule.projectId())) {
            throw new IllegalArgumentException("任务 " + schedule.id() + " 属于项目 "
                    + schedule.projectId() + "，不能在项目 " + requestedProjectId + " 中操作");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
