package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.investigation.model.InvestigationRetryAdjustment;
import cn.lgs.orbisops.domain.investigation.service.InvestigationRetryPolicy;

import java.util.Optional;

/** DTO anti-corruption layer for Investigation retry policy. */
final class OpsInvestigationRetryService {

    private static final InvestigationRetryPolicy POLICY =
            new InvestigationRetryPolicy();

    Optional<OpsAgentRunRequestDTO> adjustedRequest(
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO.InvestigationResultDTO result) {
        if (request == null || result == null) return Optional.empty();
        Optional<InvestigationRetryAdjustment> adjustment = POLICY.adjust(
                new InvestigationRetryPolicy.Input(
                        result.getSource(),
                        result.getStatus(),
                        Boolean.TRUE.equals(result.getShouldRetry()),
                        request.getRangeMinutes(),
                        request.getPromWindow()));
        return adjustment.map(value -> copyRetryRequest(request, value));
    }

    private OpsAgentRunRequestDTO copyRetryRequest(
            OpsAgentRunRequestDTO request,
            InvestigationRetryAdjustment adjustment) {
        return OpsAgentRunRequestDTO.builder()
                .runId(request.getRunId())
                .rangeMinutes(adjustment.rangeMinutes())
                .promWindow(adjustment.promWindow())
                .includeRecentLogs(request.getIncludeRecentLogs())
                .question(request.getQuestion())
                .agentDefinitionId(request.getAgentDefinitionId())
                .maxRounds(0)
                .subAgentMaxIterations(request.getSubAgentMaxIterations())
                .nodeTimeoutSeconds(request.getNodeTimeoutSeconds())
                .maxEvidenceItems(request.getMaxEvidenceItems())
                .notifyChannel(request.getNotifyChannel())
                .notificationChannelId(request.getNotificationChannelId())
                .notificationTarget(request.getNotificationTarget())
                .build();
    }
}
