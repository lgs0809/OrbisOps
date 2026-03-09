package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.investigation.model.InvestigationFinalReportTrustDecision;
import cn.lgs.orbisops.domain.investigation.service.InvestigationFinalReportTrustPolicy;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/** DTO ACL and observability boundary for final-report evidence trust. */
@Slf4j
final class OpsFinalReportTrustService {

    private static final InvestigationFinalReportTrustPolicy POLICY =
            new InvestigationFinalReportTrustPolicy();

    boolean trusted(OpsAnalysisResponseDTO response, String report) {
        InvestigationFinalReportTrustDecision decision = POLICY.assess(
                new InvestigationFinalReportTrustPolicy.Input(
                        report,
                        evidence(response),
                        results(response)));
        if (decision.trusted()) {
            return true;
        }
        switch (decision.failure()) {
            case MISSING_SOURCE_OR_GAP_SECTION -> log.warn(
                    "最终报告缺少数据源与缺口章节，analysisId={}",
                    response.getAnalysisId());
            case UNEXECUTED_SOURCE_CLAIM -> log.warn(
                    "最终报告声称使用了未执行的数据源，analysisId={}, violations={}, executedSources={}",
                    response.getAnalysisId(),
                    decision.violations(),
                    decision.executedSources());
            case MISSING_INVESTIGATION_GAP -> log.warn(
                    "最终报告未呈现调查缺口，analysisId={}",
                    response.getAnalysisId());
            case NONE -> {
            }
        }
        return false;
    }

    private InvestigationFinalReportTrustPolicy.Evidence evidence(
            OpsAnalysisResponseDTO response) {
        return new InvestigationFinalReportTrustPolicy.Evidence(
                response.getLogSummary() != null || hasItems(response.getRecentLogs()),
                response.getMetricSummary() != null || hasItems(response.getEndpointMetrics()),
                response.getSlowSqlSummary() != null || hasItems(response.getSlowSqlSamples()));
    }

    private List<InvestigationFinalReportTrustPolicy.Result> results(
            OpsAnalysisResponseDTO response) {
        return Optional.ofNullable(response.getInvestigationResults())
                .orElse(List.of())
                .stream()
                .map(result -> new InvestigationFinalReportTrustPolicy.Result(
                        result.getSource(),
                        result.getStatus(),
                        hasItems(result.getGaps())))
                .toList();
    }

    private boolean hasItems(List<?> items) {
        return items != null && !items.isEmpty();
    }
}
