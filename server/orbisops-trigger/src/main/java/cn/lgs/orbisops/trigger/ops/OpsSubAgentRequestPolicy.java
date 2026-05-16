package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.domain.runtime.investigation.model.InvestigationWindow;
import cn.lgs.orbisops.domain.runtime.investigation.service.InvestigationWindowPolicy;

/** Maps run DTOs to the pure monotonic investigation-window domain policy. */
final class OpsSubAgentRequestPolicy {

    private final InvestigationWindowPolicy windowPolicy =
            new InvestigationWindowPolicy();

    OpsAgentRunRequestDTO applyRangeDecision(
            OpsAgentRunRequestDTO request,
            Integer decisionRangeMinutes) {
        return applyLogDecision(request, decisionRangeMinutes, null);
    }

    OpsAgentRunRequestDTO applyLogDecision(
            OpsAgentRunRequestDTO request,
            Integer decisionRangeMinutes,
            Boolean includeRecentLogs) {
        InvestigationWindow target = windowPolicy.applyLogDecision(
                window(request),
                decisionRangeMinutes,
                includeRecentLogs);
        return copy(request, target);
    }

    OpsAgentRunRequestDTO expandRange(OpsAgentRunRequestDTO request) {
        return expandRange(
                request,
                request == null ? null : request.getIncludeRecentLogs());
    }

    OpsAgentRunRequestDTO expandRange(
            OpsAgentRunRequestDTO request,
            Boolean includeRecentLogs) {
        return copy(
                request,
                windowPolicy.expandRange(window(request), includeRecentLogs));
    }

    OpsAgentRunRequestDTO applyPromWindowDecision(
            OpsAgentRunRequestDTO request,
            String candidateWindow) {
        return copy(
                request,
                windowPolicy.applyPrometheusWindowDecision(
                        window(request), candidateWindow));
    }

    OpsAgentRunRequestDTO expandPromWindow(OpsAgentRunRequestDTO request) {
        return copy(
                request,
                windowPolicy.expandPrometheusWindow(window(request)));
    }

    int rangeMinutes(OpsAgentRunRequestDTO request) {
        return window(request).rangeMinutes();
    }

    private InvestigationWindow window(OpsAgentRunRequestDTO request) {
        return new InvestigationWindow(
                request == null || request.getRangeMinutes() == null
                        ? InvestigationWindowPolicy.DEFAULT_RANGE_MINUTES
                        : request.getRangeMinutes(),
                request == null ? null : request.getPromWindow(),
                request == null ? null : request.getIncludeRecentLogs());
    }

    private OpsAgentRunRequestDTO copy(
            OpsAgentRunRequestDTO request,
            InvestigationWindow window) {
        OpsAgentRunRequestDTO source = request == null
                ? new OpsAgentRunRequestDTO()
                : request;
        return OpsAgentRunRequestDTO.builder()
                .runId(source.getRunId())
                .requestedBy(source.getRequestedBy())
                .projectId(source.getProjectId())
                .agentDefinitionId(source.getAgentDefinitionId())
                .agentVersion(source.getAgentVersion())
                .agentDefinitionSnapshotJson(source.getAgentDefinitionSnapshotJson())
                .query(source.getQuery())
                .question(source.getQuestion())
                .rangeMinutes(window.rangeMinutes())
                .promWindow(window.prometheusWindow())
                .includeRecentLogs(window.includeRecentLogs())
                .maxRounds(source.getMaxRounds())
                .subAgentMaxIterations(source.getSubAgentMaxIterations())
                .nodeTimeoutSeconds(source.getNodeTimeoutSeconds())
                .maxEvidenceItems(source.getMaxEvidenceItems())
                .notifyChannel(source.getNotifyChannel())
                .notificationChannelId(source.getNotificationChannelId())
                .notificationTarget(source.getNotificationTarget())
                .triggerSource(source.getTriggerSource())
                .triggerEventId(source.getTriggerEventId())
                .build();
    }
}
