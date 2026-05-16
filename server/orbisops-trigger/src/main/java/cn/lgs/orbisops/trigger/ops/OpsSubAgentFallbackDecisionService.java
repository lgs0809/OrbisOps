package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.domain.investigation.model.InvestigationSubAgentQueryDecision;
import cn.lgs.orbisops.domain.investigation.model.InvestigationSubAgentReviewDecision;
import cn.lgs.orbisops.domain.investigation.service.InvestigationSubAgentFallbackPolicy;

/** DTO anti-corruption layer for deterministic SubAgent fallback decisions. */
final class OpsSubAgentFallbackDecisionService {

    private static final InvestigationSubAgentFallbackPolicy POLICY =
            new InvestigationSubAgentFallbackPolicy();

    OpsSubAgentDecision query(
            String source,
            OpsAgentRunRequestDTO request,
            OpsQuestionContext questionContext,
            String reason) {
        InvestigationSubAgentQueryDecision decision = POLICY.query(
                new InvestigationSubAgentFallbackPolicy.QueryInput(
                        source,
                        request.getRangeMinutes(),
                        request.getPromWindow(),
                        request.getIncludeRecentLogs(),
                        questionContext.hasRuntimeFilter(),
                        reason));
        return new OpsSubAgentDecision(
                decision.llmGenerated(),
                decision.reason(),
                decision.rangeMinutes(),
                decision.promWindow(),
                decision.includeRecentLogs(),
                decision.retrievalMode(),
                decision.queryFocus(),
                decision.requireExactFilters(),
                decision.expectedEvidence());
    }

    OpsAgentReview review(OpsAgentReview fallback, String reason) {
        InvestigationSubAgentReviewDecision decision = POLICY.review(
                new InvestigationSubAgentFallbackPolicy.ReviewInput(
                        fallback.status(),
                        fallback.summary(),
                        fallback.gaps(),
                        fallback.suggestedAdjustments(),
                        fallback.shouldRetry(),
                        fallback.confidence(),
                        reason));
        return new OpsAgentReview(
                decision.llmGenerated(),
                decision.status(),
                decision.summary(),
                decision.gaps(),
                decision.suggestedAdjustments(),
                decision.shouldRetry(),
                decision.confidence());
    }
}
