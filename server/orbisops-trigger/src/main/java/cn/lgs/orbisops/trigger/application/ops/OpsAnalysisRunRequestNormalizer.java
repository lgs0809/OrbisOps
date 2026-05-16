package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

/** Normalizes the public analysis request and freezes its authoritative Agent snapshot. */
final class OpsAnalysisRunRequestNormalizer {

    private final OpsAnalysisAgentDefinitionSnapshotResolver snapshotResolver;

    OpsAnalysisRunRequestNormalizer(OpsAnalysisAgentDefinitionSnapshotResolver snapshotResolver) {
        this.snapshotResolver = snapshotResolver;
    }

    OpsAgentRunRequestDTO normalize(OpsAgentRunRequestDTO input) {
        OpsAgentRunRequestDTO request = input == null ? new OpsAgentRunRequestDTO() : input;
        String query = text(request.getQuery());
        String question = text(request.getQuestion());
        if (!hasText(question)) {
            question = query;
        }
        if (!hasText(query)) {
            query = question;
        }
        String projectId = text(request.getProjectId());
        if (!hasText(projectId)) {
            throw new IllegalArgumentException("运行运维 Agent 前必须选择 projectId");
        }

        OpsAgentDefinition snapshot = snapshotResolver.resolve(request, projectId);
        return OpsAgentRunRequestDTO.builder()
                .runId(request.getRunId())
                .requestedBy(request.getRequestedBy())
                .projectId(projectId)
                .agentDefinitionId(snapshot == null ? request.getAgentDefinitionId() : snapshot.getAgentId())
                .agentVersion(snapshot == null ? request.getAgentVersion() : snapshot.getVersion())
                .agentDefinitionSnapshotJson(snapshot == null
                        ? request.getAgentDefinitionSnapshotJson()
                        : snapshotResolver.serialize(snapshot))
                .query(query)
                .question(question)
                .rangeMinutes(clamp(request.getRangeMinutes(), 15, 1, 1440))
                .promWindow(normalizePromWindow(request.getPromWindow()))
                .includeRecentLogs(!Boolean.FALSE.equals(request.getIncludeRecentLogs()))
                .maxRounds(request.getMaxRounds())
                .subAgentMaxIterations(clamp(request.getSubAgentMaxIterations(), 3, 1, 10))
                .nodeTimeoutSeconds(clampNullable(request.getNodeTimeoutSeconds(), 1, 300))
                .maxEvidenceItems(clampNullable(request.getMaxEvidenceItems(), 1, 50))
                .changeRequested(request.getChangeRequested())
                .notifyChannel(request.getNotifyChannel())
                .notificationChannelId(request.getNotificationChannelId())
                .notificationTarget(request.getNotificationTarget())
                .triggerSource(request.getTriggerSource())
                .executionStyle(request.getExecutionStyle())
                .triggerEventId(request.getTriggerEventId())
                .build();
    }

    private String normalizePromWindow(String value) {
        String candidate = hasText(value) ? value.trim() : "5m";
        return candidate.matches("^(1|3|5|10|15|30)m$|^1h$") ? candidate : "5m";
    }

    private Integer clampNullable(Integer value, int minimum, int maximum) {
        return value == null ? null : Math.max(minimum, Math.min(value, maximum));
    }

    private int clamp(Integer value, int defaultValue, int minimum, int maximum) {
        int candidate = value == null ? defaultValue : value;
        return Math.max(minimum, Math.min(candidate, maximum));
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
