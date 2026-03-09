package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.alert.AlertOutboxApplicationService;
import cn.lgs.orbisops.application.alert.AlertOutboxBatchResult;
import cn.lgs.orbisops.application.alert.AlertTriggerOutboxOutcome;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationDecision;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertOutboxMapper;
import cn.lgs.orbisops.trigger.ops.OpsAlertWebhookProtocolService.AlertView;

import java.util.Map;
import java.util.function.Function;

/** Analysis request creation, durable enqueue, and bounded dispatch boundary. */
public final class OpsAlertRunSubmissionCoordinator {

    private final OpsAnalysisRunService analysisRuns;
    private final AlertOutboxApplicationService alertOutbox;
    private final OpsAlertOutboxMapper mapper;
    private final OpsAlertAnalysisRequestFactory requestFactory;
    private final OpsAgentDefinitionQueryGateway agentDefinitions;
    private final OpsAlertTriggerSettings settings;
    private final String sourceType;

    public OpsAlertRunSubmissionCoordinator(
            OpsAnalysisRunService analysisRuns,
            AlertOutboxApplicationService alertOutbox,
            OpsAlertOutboxMapper mapper,
            OpsAlertAnalysisRequestFactory requestFactory,
            OpsAgentDefinitionQueryGateway agentDefinitions,
            OpsAlertTriggerSettings settings,
            String sourceType) {
        this.analysisRuns = analysisRuns;
        this.alertOutbox = alertOutbox;
        this.mapper = mapper;
        this.requestFactory = requestFactory;
        this.agentDefinitions = agentDefinitions;
        this.settings = settings == null ? OpsAlertTriggerSettings.defaults() : settings;
        this.sourceType = sourceType;
    }

    public SubmissionOutcome enqueueAndDispatch(
            OpsAlertTriggerRule rule,
            AlertView alert,
            AlertAggregationDecision aggregation,
            Map<String, Object> rawAlert,
            Function<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> analyzer) {
        enqueue(
                rule,
                alert,
                aggregation.dispatchKey(),
                aggregation.aggregateKey(),
                aggregation.eventType().name(),
                aggregation.priority(),
                aggregation.occurrenceCount(),
                rawAlert);
        String runId = alertOutbox.dispatchIfCapacity(
                        rule.getProjectId(),
                        settings.projectMaxRunning(),
                        aggregation.dispatchKey(),
                        settings.outboxMaxAttempts(),
                        submitted -> analysisRuns.submit(mapper.request(submitted), analyzer).getRunId())
                .orElse(null);
        return new SubmissionOutcome(runId, runId != null && !runId.isBlank());
    }

    public OpsAgentRunRequestDTO enqueue(
            OpsAlertTriggerRule rule,
            AlertView alert,
            String dispatchKey,
            String aggregateKey,
            String eventType,
            int priority,
            long occurrenceCount,
            Map<String, Object> rawAlert) {
        OpsAgentRunRequestDTO request = requestFactory.build(
                agentDefinitions,
                rule,
                alert,
                eventType,
                occurrenceCount,
                sourceType);
        alertOutbox.enqueue(
                mapper.draft(
                        dispatchKey,
                        rule.getId(),
                        rule.getProjectId(),
                        alert.fingerprint(),
                        aggregateKey,
                        eventType,
                        priority,
                        request,
                        rawAlert),
                settings.projectMaxQueued());
        return request;
    }

    public AlertTriggerOutboxOutcome processPending(
            int limit,
            int summariesQueued,
            Function<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> analyzer) {
        AlertOutboxBatchResult result = alertOutbox.processPending(
                limit,
                settings.outboxMaxAttempts(),
                settings.outboxLockTimeoutSeconds(),
                settings.projectMaxRunning(),
                submitted -> analysisRuns.submit(mapper.request(submitted), analyzer).getRunId());
        return mapper.batchResult(result, summariesQueued);
    }

    public record SubmissionOutcome(String runId, boolean triggered) {
    }
}
