package cn.lgs.orbisops.application.schedule;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Application process manager for project-scoped scheduled Agent task configuration. */
public final class TaskScheduleCatalogUseCase {

    private static final int MAX_TASK_NAME_CHARS = 128;
    private static final int MAX_DESCRIPTION_CHARS = 255;

    private final TaskScheduleCatalogPort catalogPort;
    private final TaskScheduleAuditPort auditPort;
    private final TaskScheduleAgentSnapshotPort agentSnapshotPort;
    private final TaskScheduleCronValidationPort cronValidationPort;
    private final Clock clock;

    public TaskScheduleCatalogUseCase(
            TaskScheduleCatalogPort catalogPort,
            TaskScheduleAuditPort auditPort,
            TaskScheduleAgentSnapshotPort agentSnapshotPort,
            TaskScheduleCronValidationPort cronValidationPort) {
        this(catalogPort, auditPort, agentSnapshotPort, cronValidationPort, Clock.systemDefaultZone());
    }

    public TaskScheduleCatalogUseCase(
            TaskScheduleCatalogPort catalogPort,
            TaskScheduleAuditPort auditPort,
            TaskScheduleAgentSnapshotPort agentSnapshotPort,
            TaskScheduleCronValidationPort cronValidationPort,
            Clock clock) {
        if (catalogPort == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_CATALOG_PORT_REQUIRED");
        }
        if (auditPort == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_AUDIT_PORT_REQUIRED");
        }
        if (agentSnapshotPort == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_AGENT_SNAPSHOT_PORT_REQUIRED");
        }
        if (cronValidationPort == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_CRON_VALIDATION_PORT_REQUIRED");
        }
        if (clock == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_CLOCK_REQUIRED");
        }
        this.catalogPort = catalogPort;
        this.auditPort = auditPort;
        this.agentSnapshotPort = agentSnapshotPort;
        this.cronValidationPort = cronValidationPort;
        this.clock = clock;
    }

    public List<TaskScheduleDefinition> listSchedules(String projectId) {
        String normalizedProjectId = requireProjectId(projectId, "查询巡检任务必须提供 projectId");
        return immutable(catalogPort.findByProjectId(normalizedProjectId));
    }

    public boolean create(TaskScheduleCommand requested) {
        return create(requested, null);
    }

    public boolean create(TaskScheduleCommand requested, String createdBy) {
        String owner = requireText(createdBy, "TASK_SCHEDULE_CREATOR_REQUIRED");
        LocalDateTime now = LocalDateTime.now(clock);
        TaskScheduleDefinition schedule = normalize(requireCommand(requested), null, now, owner);
        boolean created = catalogPort.insert(schedule);
        if (created) {
            auditPort.created(schedule);
        }
        return created;
    }

    public boolean update(TaskScheduleCommand requested) {
        TaskScheduleCommand command = requireCommand(requested);
        if (command.id() == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        TaskScheduleDefinition before = requireSchedule(command.id());
        assertProject(before, command.projectId());
        TaskScheduleDefinition after = normalize(command, before, LocalDateTime.now(clock), before.createdBy());
        boolean updated = catalogPort.update(after);
        if (updated) {
            auditPort.updated(command.id(), before, after);
        }
        return updated;
    }

    public boolean updateStatus(Long id, Integer status, String projectId) {
        if (id == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        TaskScheduleDefinition before = requireSchedule(id);
        assertProject(before, projectId);
        TaskScheduleDefinition after = before.withStatus(
                Integer.valueOf(1).equals(status) ? 1 : 0,
                LocalDateTime.now(clock));
        boolean updated = catalogPort.update(after);
        if (updated) {
            auditPort.statusChanged(id, before, after);
        }
        return updated;
    }

    public boolean delete(Long id, String projectId) {
        if (id == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        TaskScheduleDefinition before = requireSchedule(id);
        assertProject(before, projectId);
        boolean deleted = catalogPort.deleteById(id);
        if (deleted) {
            auditPort.deleted(id, before);
        }
        return deleted;
    }

    private TaskScheduleDefinition normalize(
            TaskScheduleCommand command,
            TaskScheduleDefinition before,
            LocalDateTime now, String createdBy) {
        String taskName = requireText(command.taskName(), "任务名称不能为空");
        String cronExpression = requireText(command.cronExpression(), "Cron 表达式不能为空");
        String projectId = requireProjectId(command.projectId(), "业务系统 projectId 不能为空");
        String agentId = requireText(command.agentId(), "执行 Agent 不能为空");
        cronValidationPort.validate(cronExpression);

        String executionType = executionType(command.executionType());
        String bindingMode = agentBindingMode(command.agentBindingMode());
        if ("DEFAULT_REACT".equals(executionType)) {
            bindingMode = "LATEST_PUBLISHED";
        }
        Integer requestedVersion = "PINNED_VERSION".equals(bindingMode) ? command.agentVersion() : null;
        if ("PINNED_VERSION".equals(bindingMode)
                && (requestedVersion == null || requestedVersion <= 0)) {
            throw new IllegalArgumentException("PINNED_VERSION 巡检任务必须选择 Agent 版本");
        }
        TaskScheduleAgentSnapshot snapshot = agentSnapshotPort.resolve(agentId, requestedVersion, projectId);
        if (snapshot == null
                || snapshot.version() == null
                || snapshot.version() <= 0
                || !hasText(snapshot.definitionHash())) {
            throw new IllegalStateException("SCHEDULE_AGENT_VERSION_INCOMPLETE");
        }

        TaskScheduleRuntimeConfiguration runtime = new TaskScheduleRuntimeConfiguration(
                projectId,
                executionType,
                bindingMode,
                snapshot.version(),
                snapshot.definitionHash(),
                command.prompt(),
                clamp(command.rangeMinutes(), 15, 1, 1440),
                normalizePromWindow(command.promWindow()),
                !Boolean.FALSE.equals(command.includeRecentLogs()),
                clamp(command.maxRounds(), 3, 1, 20),
                clamp(command.subAgentMaxIterations(), 3, 1, 10),
                clamp(command.nodeTimeoutSeconds(), 120, 1, 300),
                clamp(command.maxEvidenceItems(), 12, 1, 50),
                Boolean.TRUE.equals(command.notifyChannel()),
                trimToNull(command.notificationChannelId()),
                trimToNull(command.notificationTarget()),
                new TaskScheduleScreeningConfiguration(
                        Boolean.TRUE.equals(command.lightweightScreeningEnabled()),
                        command.screeningSourceType(),
                        command.screeningPrimaryUri(),
                        command.maxErrorRatePercent(),
                        command.maxCpuPercent(),
                        command.maxHeapPercent(),
                        command.minInstanceUpRatio()));

        return new TaskScheduleDefinition(
                command.id(),
                projectId,
                agentId,
                abbreviate(taskName, MAX_TASK_NAME_CHARS),
                abbreviate(command.description(), MAX_DESCRIPTION_CHARS),
                cronExpression,
                runtime,
                command.status() == null ? before == null ? 1 : before.status() : command.status(),
                before == null ? now : before.createTime(),
                now, createdBy);
    }

    private TaskScheduleDefinition requireSchedule(Long id) {
        TaskScheduleDefinition schedule = catalogPort.findById(id);
        if (schedule == null) {
            throw new IllegalArgumentException("任务不存在：" + id);
        }
        return schedule;
    }

    private TaskScheduleCommand requireCommand(TaskScheduleCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        return command;
    }

    private void assertProject(TaskScheduleDefinition schedule, String projectId) {
        String requestedProjectId = requireProjectId(projectId, "操作巡检任务必须提供 projectId");
        if (!requestedProjectId.equals(schedule.projectId())) {
            throw new IllegalArgumentException("任务 " + schedule.id() + " 属于项目 "
                    + schedule.projectId() + "，不能在项目 " + requestedProjectId + " 中操作");
        }
    }

    private String requireProjectId(String value, String message) {
        return requireText(value, message);
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String executionType(String value) {
        String type = hasText(value) ? value.trim().toUpperCase() : "WORKFLOW";
        if (!List.of("DEFAULT_REACT", "WORKFLOW").contains(type)) {
            throw new IllegalArgumentException("执行方式只允许 DEFAULT_REACT 或 WORKFLOW");
        }
        return type;
    }

    private String agentBindingMode(String value) {
        String mode = hasText(value) ? value.trim().toUpperCase() : "LATEST_PUBLISHED";
        if (!List.of("LATEST_PUBLISHED", "PINNED_VERSION").contains(mode)) {
            throw new IllegalArgumentException("Agent 绑定模式只允许 LATEST_PUBLISHED 或 PINNED_VERSION");
        }
        return mode;
    }

    private int clamp(Integer value, int defaultValue, int min, int max) {
        return Math.max(min, Math.min(value == null ? defaultValue : value, max));
    }

    private String normalizePromWindow(String promWindow) {
        String value = hasText(promWindow) ? promWindow.trim() : "5m";
        return value.matches("^(1|3|5|10|15|30)m$|^1h$") ? value : "5m";
    }

    private String abbreviate(String value, int maxChars) {
        if (!hasText(value)) {
            return value;
        }
        String text = value.trim();
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private List<TaskScheduleDefinition> immutable(List<TaskScheduleDefinition> values) {
        return values == null || values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
