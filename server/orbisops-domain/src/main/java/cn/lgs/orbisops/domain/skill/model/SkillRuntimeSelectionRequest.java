package cn.lgs.orbisops.domain.skill.model;

import java.util.LinkedHashSet;
import java.util.Set;

public record SkillRuntimeSelectionRequest(String projectId,
                                           String agentId,
                                           String query,
                                           Set<String> requestedSkillIds,
                                           int catalogCandidateLimit,
                                           int selectedLimit,
                                           int maxExplicitSkills,
                                           double minRelevanceScore,
                                           double similarSuppressionThreshold,
                                           double semanticWeight,
                                           int semanticRecallLimit,
                                           double categoryBoost,
                                           double negativePenaltyWeight) {

    public SkillRuntimeSelectionRequest {
        projectId = safe(projectId);
        agentId = safe(agentId);
        query = safe(query);
        requestedSkillIds = requestedSkillIds == null
                ? Set.of() : Set.copyOf(new LinkedHashSet<>(requestedSkillIds));
        catalogCandidateLimit = clamp(catalogCandidateLimit, 1, 100);
        selectedLimit = clamp(selectedLimit, 1, 20);
        maxExplicitSkills = clamp(maxExplicitSkills, 1, 50);
        minRelevanceScore = clamp(minRelevanceScore, 0D, 1D);
        similarSuppressionThreshold = clamp(similarSuppressionThreshold, 0D, 1D);
        semanticWeight = clamp(semanticWeight, 0D, 0.8D);
        semanticRecallLimit = clamp(semanticRecallLimit, 1, 256);
        categoryBoost = clamp(categoryBoost, 0D, 0.5D);
        negativePenaltyWeight = clamp(negativePenaltyWeight, 0D, 1D);
    }

    public SkillRuntimeSelectionRequest(String projectId,
                                        String agentId,
                                        String query,
                                        Set<String> requestedSkillIds,
                                        int catalogCandidateLimit,
                                        int selectedLimit,
                                        int maxExplicitSkills,
                                        double minRelevanceScore,
                                        double similarSuppressionThreshold,
                                        double semanticWeight) {
        this(projectId, agentId, query, requestedSkillIds, catalogCandidateLimit,
                selectedLimit, maxExplicitSkills, minRelevanceScore,
                similarSuppressionThreshold, semanticWeight, 64, 0.12D, 0.65D);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
