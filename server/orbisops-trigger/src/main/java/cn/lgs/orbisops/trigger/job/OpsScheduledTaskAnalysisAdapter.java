package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.schedule.ScheduledTaskAnalysisPort;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCommand;
import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import cn.lgs.orbisops.domain.agentdefinition.service.DirectActionDataPolicy;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.trigger.application.ops.OpsAnalysisApplicationService;
import cn.lgs.orbisops.trigger.ops.OpsJsonSnapshotCodec;
import com.alibaba.fastjson.JSON;
import org.springframework.util.StringUtils;

/** Ops Agent runtime protocol adapter for scheduled task execution. */
public final class OpsScheduledTaskAnalysisAdapter implements
        ScheduledTaskAnalysisPort<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> {

    private final OpsAnalysisApplicationService analysisService;
    private final cn.lgs.orbisops.trigger.application.config.OpsTaskScheduleRuntimeConfigurationCodec runtimeCodec;
    private final ProjectDefinitionApplicationService projects;

    public OpsScheduledTaskAnalysisAdapter(
            OpsAnalysisApplicationService analysisService,
            cn.lgs.orbisops.trigger.application.config.OpsTaskScheduleRuntimeConfigurationCodec runtimeCodec) {
        this(analysisService, runtimeCodec, null);
    }

    public OpsScheduledTaskAnalysisAdapter(
            OpsAnalysisApplicationService analysisService,
            cn.lgs.orbisops.trigger.application.config.OpsTaskScheduleRuntimeConfigurationCodec runtimeCodec,
            ProjectDefinitionApplicationService projects) {
        if (analysisService == null) {
            throw new IllegalArgumentException("OPS_ANALYSIS_APPLICATION_SERVICE_REQUIRED");
        }
        if (runtimeCodec == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_RUNTIME_CODEC_REQUIRED");
        }
        this.analysisService = analysisService;
        this.runtimeCodec = runtimeCodec;
        this.projects = projects;
    }

    @Override
    public OpsAgentRunRequestDTO prepare(ScheduledTaskExecutionCommand command, Long executionId) {
        if (text(command.createdBy()).isBlank()) {
            throw new IllegalArgumentException("TASK_SCHEDULE_CREATOR_MISSING");
        }
        TaskScheduleRuntimeConfiguration config = runtimeCodec.decode(command.runtimePayload());
        String defaultAgentId = projects == null ? "" : text(projects.defaultAgentId(config.projectId()));
        String selectedAgentId = StringUtils.hasText(command.agentId())
                ? command.agentId().trim()
                : defaultAgentId;
        boolean explicitWorkflow = StringUtils.hasText(command.agentId())
                && (!StringUtils.hasText(defaultAgentId)
                || !defaultAgentId.equals(selectedAgentId));
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .runId("task_" + command.scheduleId() + "_" + executionId)
                .requestedBy(command.createdBy())
                .projectId(config.projectId())
                .rangeMinutes(config.rangeMinutes() == null ? 15 : config.rangeMinutes())
                .promWindow(StringUtils.hasText(config.promWindow()) ? config.promWindow() : "5m")
                .includeRecentLogs(!Boolean.FALSE.equals(config.includeRecentLogs()))
                .question(scheduledQuestion(command.taskName(), config.prompt(), explicitWorkflow))
                .agentDefinitionId(selectedAgentId)
                .agentVersion("PINNED_VERSION".equalsIgnoreCase(config.agentBindingMode())
                        ? config.agentVersion() : null)
                .maxRounds(config.maxRounds())
                .subAgentMaxIterations(config.subAgentMaxIterations() == null
                        ? 3 : config.subAgentMaxIterations())
                .nodeTimeoutSeconds(config.nodeTimeoutSeconds())
                .maxEvidenceItems(config.maxEvidenceItems())
                .notifyChannel(Boolean.TRUE.equals(config.notifyChannel()))
                .notificationChannelId(config.notificationChannelId())
                .notificationTarget(config.notificationTarget())
                .triggerSource("task-schedule")
                .executionStyle(explicitWorkflow ? "WORKFLOW" : "REACT")
                .triggerEventId(String.valueOf(executionId))
                .build();
        return analysisService.normalizeRequest(request);
    }

    @Override
    public String serializeInput(OpsAgentRunRequestDTO request) {
        return JSON.toJSONString(request);
    }

    @Override
    public OpsAnalysisResponseDTO execute(OpsAgentRunRequestDTO request) {
        return analysisService.buildAnalysis(request);
    }

    @Override
    public String renderOutput(OpsAnalysisResponseDTO response) {
        if (StringUtils.hasText(response.getMarkdownReport())) return response.getMarkdownReport();
        if (StringUtils.hasText(response.getAiPrompt())) return response.getAiPrompt();
        return OpsJsonSnapshotCodec.write(response);
    }

    @Override
    public String runtimeStatus(OpsAnalysisResponseDTO response) {
        return response == null ? "UNKNOWN" : text(response.getRuntimeStatus());
    }

    private String scheduledQuestion(String taskName, String prompt, boolean explicitWorkflow) {
        if (explicitWorkflow && StringUtils.hasText(prompt) && prompt.stripLeading().startsWith("{")) {
            var input = new DirectActionDataPolicy().parseObject(prompt);
            return CanonicalJson.stringifyPreservingOrder(input);
        }
        String name = StringUtils.hasText(taskName) ? taskName.trim() : "周期 Agent 任务";
        return StringUtils.hasText(prompt)
                ? "定时任务：" + name + "\n执行要求：" + prompt.trim()
                : "执行定时任务：" + name;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
