package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.TaskExecutionResponseDTO;
import cn.lgs.orbisops.api.dto.TaskScheduleRequestDTO;
import cn.lgs.orbisops.api.dto.TaskScheduleResponseDTO;
import cn.lgs.orbisops.application.schedule.TaskExecutionView;
import cn.lgs.orbisops.application.schedule.TaskScheduleCatalogUseCase;
import cn.lgs.orbisops.application.schedule.TaskScheduleCommand;
import cn.lgs.orbisops.application.schedule.TaskScheduleDefinition;
import cn.lgs.orbisops.application.schedule.TaskScheduleExecutionUseCase;
import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** HTTP-facing facade for scheduled Agent task configuration and execution. */
@Service
public class TaskScheduleApplicationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TaskScheduleCatalogUseCase catalogUseCase;
    private final TaskScheduleExecutionUseCase executionUseCase;

    public TaskScheduleApplicationService(
            TaskScheduleCatalogUseCase catalogUseCase,
            TaskScheduleExecutionUseCase executionUseCase) {
        if (catalogUseCase == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_CATALOG_USE_CASE_REQUIRED");
        }
        if (executionUseCase == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_EXECUTION_USE_CASE_REQUIRED");
        }
        this.catalogUseCase = catalogUseCase;
        this.executionUseCase = executionUseCase;
    }

    public List<TaskScheduleResponseDTO> listSchedules(String projectId) {
        return catalogUseCase.listSchedules(projectId).stream().map(this::toResponse).toList();
    }

    public boolean create(TaskScheduleRequestDTO request) {
        return catalogUseCase.create(toCommand(request), creator());
    }

    public boolean update(TaskScheduleRequestDTO request) {
        return catalogUseCase.update(toCommand(request));
    }

    public boolean updateStatus(Long id, Integer status, String projectId) {
        return catalogUseCase.updateStatus(id, status, projectId);
    }

    public boolean delete(Long id, String projectId) {
        return catalogUseCase.delete(id, projectId);
    }

    public Long runNow(Long id, String projectId) {
        return executionUseCase.runNow(id, projectId);
    }

    public List<TaskExecutionResponseDTO> listExecutions(String projectId, Long scheduleId, Integer limit) {
        return executionUseCase.listExecutions(projectId, scheduleId, limit).stream()
                .map(this::toExecutionResponse)
                .toList();
    }

    private String creator() {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        Object principal = attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet
                ? servlet.getRequest().getAttribute(cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE) : null;
        if (!(principal instanceof cn.lgs.orbisops.trigger.application.security.AdminAuthService.AuthPrincipal actor)
                || actor.serviceToken() || actor.userId() == null || actor.userId().isBlank()) {
            throw new IllegalArgumentException("TASK_SCHEDULE_CREATOR_REQUIRED");
        }
        return actor.userId();
    }

    private TaskScheduleCommand toCommand(TaskScheduleRequestDTO request) {
        if (request == null) {
            return null;
        }
        return new TaskScheduleCommand(
                request.getId(),
                request.getProjectId(),
                request.getExecutionType(),
                request.getAgentId(),
                request.getAgentBindingMode(),
                request.getAgentVersion(),
                request.getTaskName(),
                request.getDescription(),
                request.getCronExpression(),
                request.getTaskParam(),
                request.getStatus(),
                request.getRangeMinutes(),
                request.getPromWindow(),
                request.getIncludeRecentLogs(),
                request.getMaxRounds(),
                request.getSubAgentMaxIterations(),
                request.getNodeTimeoutSeconds(),
                request.getMaxEvidenceItems(),
                request.getNotifyChannel(),
                request.getNotificationChannelId(),
                request.getNotificationTarget(),
                request.getLightweightScreeningEnabled(),
                request.getScreeningSourceType(),
                request.getScreeningPrimaryUri(),
                request.getMaxErrorRatePercent(),
                request.getMaxCpuPercent(),
                request.getMaxHeapPercent(),
                request.getMinInstanceUpRatio());
    }

    private TaskScheduleResponseDTO toResponse(TaskScheduleDefinition schedule) {
        TaskScheduleRuntimeConfiguration runtime = schedule.runtimeConfiguration();
        return TaskScheduleResponseDTO.builder()
                .id(schedule.id())
                .projectId(schedule.projectId())
                .createdBy(schedule.createdBy())
                .executionType(runtime.executionType())
                .agentId(schedule.agentId())
                .agentBindingMode(runtime.agentBindingMode())
                .agentVersion(runtime.agentVersion())
                .agentDefinitionHash(runtime.agentDefinitionHash())
                .taskName(schedule.taskName())
                .description(schedule.description())
                .cronExpression(schedule.cronExpression())
                .taskParam(runtime.prompt())
                .status(schedule.status())
                .rangeMinutes(runtime.rangeMinutes())
                .promWindow(runtime.promWindow())
                .includeRecentLogs(runtime.includeRecentLogs())
                .maxRounds(runtime.maxRounds())
                .subAgentMaxIterations(runtime.subAgentMaxIterations())
                .nodeTimeoutSeconds(runtime.nodeTimeoutSeconds())
                .maxEvidenceItems(runtime.maxEvidenceItems())
                .notifyChannel(runtime.notifyChannel())
                .notificationChannelId(runtime.notificationChannelId())
                .notificationTarget(runtime.notificationTarget())
                .lightweightScreeningEnabled(runtime.screening().enabled())
                .screeningSourceType(runtime.screening().sourceType())
                .screeningPrimaryUri(runtime.screening().primaryUri())
                .maxErrorRatePercent(runtime.screening().maxErrorRatePercent())
                .maxCpuPercent(runtime.screening().maxCpuPercent())
                .maxHeapPercent(runtime.screening().maxHeapPercent())
                .minInstanceUpRatio(runtime.screening().minInstanceUpRatio())
                .createTime(format(schedule.createTime()))
                .updateTime(format(schedule.updateTime()))
                .build();
    }

    private TaskExecutionResponseDTO toExecutionResponse(TaskExecutionView execution) {
        return TaskExecutionResponseDTO.builder()
                .id(execution.id())
                .scheduleId(execution.scheduleId())
                .taskName(execution.taskName())
                .agentId(execution.agentId())
                .triggerType(execution.triggerType())
                .status(execution.status())
                .startedAt(format(execution.startedAt()))
                .endedAt(format(execution.endedAt()))
                .input(execution.input())
                .output(execution.output())
                .errorMessage(execution.errorMessage())
                .build();
    }

    private String format(LocalDateTime time) {
        return time == null ? null : DATE_TIME_FORMATTER.format(time);
    }
}
