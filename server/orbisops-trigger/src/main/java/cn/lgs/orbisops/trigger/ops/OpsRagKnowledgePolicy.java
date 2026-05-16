package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.domain.runtime.retrieval.service.KnowledgeRetrievalPolicy;

/** Trigger adapter over the pure knowledge retrieval retry policy. */
final class OpsRagKnowledgePolicy {

    private final KnowledgeRetrievalPolicy domainPolicy =
            new KnowledgeRetrievalPolicy();

    String retrievalModeForIteration(
            String preferredMode,
            int iteration,
            OpsQuestionContext questionContext) {
        return domainPolicy.retrievalModeForIteration(
                preferredMode,
                iteration,
                questionContext != null && questionContext.hasRuntimeFilter());
    }

    String buildQuery(
            OpsAgentRunRequestDTO request,
            OpsSubAgentDecision decision) {
        return domainPolicy.buildQuery(
                request == null ? null : request.getQuestion(),
                decision != null && decision.llmGenerated(),
                decision == null ? null : decision.queryFocus());
    }
}
