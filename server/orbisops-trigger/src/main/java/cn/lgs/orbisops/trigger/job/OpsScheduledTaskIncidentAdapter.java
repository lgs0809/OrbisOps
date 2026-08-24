package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.incident.AppendIncidentTimelineCommand;
import cn.lgs.orbisops.application.incident.CreateIncidentCommand;
import cn.lgs.orbisops.application.incident.IncidentCommandApplicationService;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCommand;
import cn.lgs.orbisops.application.schedule.ScheduledTaskIncidentPort;
import cn.lgs.orbisops.application.schedule.ScheduledTaskScreeningResult;
import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import cn.lgs.orbisops.trigger.application.config.OpsTaskScheduleRuntimeConfigurationCodec;

import java.util.List;
import java.util.Map;

/** Aggregates anomalous scheduled checks into the Incident business read model. */
public final class OpsScheduledTaskIncidentAdapter implements ScheduledTaskIncidentPort {

    private static final String ACTOR = "schedule";

    private final IncidentCommandApplicationService incidents;
    private final OpsTaskScheduleRuntimeConfigurationCodec runtimeCodec;

    public OpsScheduledTaskIncidentAdapter(
            IncidentCommandApplicationService incidents,
            OpsTaskScheduleRuntimeConfigurationCodec runtimeCodec) {
        if (incidents == null) throw new IllegalArgumentException("INCIDENT_COMMAND_SERVICE_REQUIRED");
        if (runtimeCodec == null) throw new IllegalArgumentException("TASK_SCHEDULE_RUNTIME_CODEC_REQUIRED");
        this.incidents = incidents;
        this.runtimeCodec = runtimeCodec;
    }

    @Override
    public String openForAnomaly(
            ScheduledTaskExecutionCommand command,
            Long executionId,
            ScheduledTaskScreeningResult screening) {
        if (text(command.createdBy()).isBlank()) throw new IllegalArgumentException("TASK_SCHEDULE_CREATOR_MISSING");
        TaskScheduleRuntimeConfiguration runtime = runtimeCodec.decode(command.runtimePayload());
        String projectId = runtime.projectId();
        String title = text(command.taskName()).isBlank()
                ? "巡检发现异常"
                : command.taskName().trim() + " · 巡检异常";
        String conditionFingerprint = screening.abnormalSources().stream()
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("INCONCLUSIVE");
        String recurringKey = "SCHEDULE:" + command.scheduleId() + ":" + conditionFingerprint;
        var incident = incidents.openRecurring(recurringKey, new CreateIncidentCommand(
                projectId,
                title,
                "OPEN",
                "WARNING",
                projectId,
                "SCHEDULE",
                screening.summary(),
                Map.of("scheduleId", command.scheduleId(), "conditionFingerprint", conditionFingerprint),
                Map.of(
                        "scheduleId", command.scheduleId(),
                        "executionId", executionId,
                        "triggerType", text(command.triggerType()),
                        "conditionFingerprint", conditionFingerprint,
                        "abnormalSources", screening.abnormalSources()),
                screening.abnormalSources()), ACTOR);
        if (text(incident.ownerUserId()).isBlank()) {
            incidents.assignOwner(incident.incidentId(), command.createdBy(), ACTOR);
        }
        incidents.appendTimeline(
                incident.incidentId(),
                new AppendIncidentTimelineCommand(
                        "SCHEDULE_ANOMALY",
                        "轻量巡检发现异常",
                        screening.summary(),
                        "SCHEDULE",
                        String.valueOf(command.scheduleId()),
                        Map.of(
                                "executionId", executionId,
                                "abnormalSources", screening.abnormalSources())),
                ACTOR);
        incidents.appendTimeline(
                incident.incidentId(),
                new AppendIncidentTimelineCommand(
                        "INVESTIGATION_STARTED",
                        "启动深度 AI 调查",
                        "轻量检查无法判定为正常，进入项目默认 Agent 深度调查。",
                        "SCHEDULE_EXECUTION",
                        String.valueOf(executionId),
                        Map.of("executionId", executionId)),
                ACTOR);
        return incident.incidentId();
    }

    @Override
    public void linkInvestigation(
            String incidentId,
            ScheduledTaskExecutionCommand command,
            Long executionId) {
        incidents.linkRun(
                incidentId,
                "task_" + command.scheduleId() + "_" + executionId,
                ACTOR,
                "巡检异常后的深度 Agent 调查已完成运行创建并回链 Incident");
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
