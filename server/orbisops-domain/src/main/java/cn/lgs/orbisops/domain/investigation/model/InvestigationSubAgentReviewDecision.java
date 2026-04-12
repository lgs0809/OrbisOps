package cn.lgs.orbisops.domain.investigation.model;

import java.util.List;

/** Framework-free SubAgent review decision. */
public record InvestigationSubAgentReviewDecision(
        boolean llmGenerated,
        String status,
        String summary,
        List<String> gaps,
        List<String> suggestedAdjustments,
        Boolean shouldRetry,
        Double confidence) {
}
