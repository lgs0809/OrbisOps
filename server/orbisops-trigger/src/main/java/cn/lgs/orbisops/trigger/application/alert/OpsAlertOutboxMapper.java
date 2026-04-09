package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.alert.AlertOutboxBatchResult;
import cn.lgs.orbisops.application.alert.AlertTriggerOutboxOutcome;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateEventType;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxDraft;
import cn.lgs.orbisops.domain.alert.model.AlertRunRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpsAlertOutboxMapper {

    public AlertOutboxDraft draft(
            String dispatchKey,
            long ruleId,
            String projectId,
            String fingerprint,
            String aggregateKey,
            String eventType,
            int priority,
            OpsAgentRunRequestDTO request,
            Map<String, Object> payload) {
        return new AlertOutboxDraft(
                dispatchKey,
                ruleId,
                projectId,
                fingerprint,
                aggregateKey,
                AlertAggregateEventType.valueOf(eventType),
                priority,
                request(request),
                payload);
    }

    public AlertRunRequest request(OpsAgentRunRequestDTO request) {
        if (request == null) throw new IllegalArgumentException("ALERT_OUTBOX_REQUEST_REQUIRED");
        return new AlertRunRequest(
                request.getRunId(),
                request.getRequestedBy(),
                request.getProjectId(),
                request.getAgentDefinitionId(),
                request.getAgentVersion(),
                request.getAgentDefinitionSnapshotJson(),
                request.getQuery(),
                request.getQuestion(),
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
                request.getExecutionStyle(),
                request.getTriggerSource(),
                request.getTriggerEventId());
    }

    public OpsAgentRunRequestDTO request(AlertRunRequest request) {
        if (request == null) throw new IllegalArgumentException("ALERT_OUTBOX_REQUEST_REQUIRED");
        return OpsAgentRunRequestDTO.builder()
                .runId(request.runId())
                .requestedBy(request.requestedBy())
                .projectId(request.projectId())
                .agentDefinitionId(request.agentDefinitionId())
                .agentVersion(request.agentVersion())
                .agentDefinitionSnapshotJson(request.agentDefinitionSnapshotJson())
                .query(request.query())
                .question(request.question())
                .rangeMinutes(request.rangeMinutes())
                .promWindow(request.promWindow())
                .includeRecentLogs(request.includeRecentLogs())
                .maxRounds(request.maxRounds())
                .subAgentMaxIterations(request.subAgentMaxIterations())
                .nodeTimeoutSeconds(request.nodeTimeoutSeconds())
                .maxEvidenceItems(request.maxEvidenceItems())
                .notifyChannel(request.notifyChannel())
                .notificationChannelId(request.notificationChannelId())
                .notificationTarget(request.notificationTarget())
                .executionStyle(request.executionStyle())
                .triggerSource(request.triggerSource())
                .triggerEventId(request.triggerEventId())
                .build();
    }

    public AlertTriggerOutboxOutcome batchResult(
            AlertOutboxBatchResult result,
            int summariesQueued) {
        return AlertTriggerOutboxOutcome.from(result, summariesQueued);
    }
}
