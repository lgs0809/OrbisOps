package cn.lgs.orbisops.application.skill;

/** Runtime Skill selection budgets supplied by the outer configuration adapter. */
public record SkillRuntimeSelectionSettings(int catalogCandidateLimit,
                                            int selectedLimit,
                                            int maxExplicitSkills,
                                            double minRelevanceScore,
                                            double similarSuppressionThreshold,
                                            double semanticWeight,
                                            int semanticRecallLimit,
                                            double categoryBoost,
                                            double negativePenaltyWeight,
                                            boolean llmRerankEnabled,
                                            int llmRerankCandidateLimit,
                                            double llmRerankWeight) {

    public SkillRuntimeSelectionSettings {
        catalogCandidateLimit = clamp(catalogCandidateLimit, 1, 20);
        selectedLimit = clamp(selectedLimit, 1, 3);
        maxExplicitSkills = clamp(maxExplicitSkills, 1, 3);
        minRelevanceScore = clamp(minRelevanceScore, 0D, 1D);
        similarSuppressionThreshold = clamp(similarSuppressionThreshold, 0D, 1D);
        semanticWeight = clamp(semanticWeight, 0D, 0.8D);
        semanticRecallLimit = 20;
        categoryBoost = clamp(categoryBoost, 0D, 0.5D);
        negativePenaltyWeight = clamp(negativePenaltyWeight, 0D, 1D);
        llmRerankCandidateLimit = 20;
        llmRerankWeight = clamp(llmRerankWeight, 0D, 0.8D);
    }

    public SkillRuntimeSelectionSettings(int catalogCandidateLimit,
                                         int selectedLimit,
                                         int maxExplicitSkills,
                                         double minRelevanceScore,
                                         double similarSuppressionThreshold,
                                         double semanticWeight) {
        this(catalogCandidateLimit, selectedLimit, maxExplicitSkills,
                minRelevanceScore, similarSuppressionThreshold, semanticWeight,
                64, 0.12D, 0.65D, true, 10, 0.35D);
    }

    public SkillRuntimeSelectionSettings(int catalogCandidateLimit,
                                         int selectedLimit,
                                         int maxExplicitSkills,
                                         double minRelevanceScore,
                                         double similarSuppressionThreshold,
                                         double semanticWeight,
                                         int semanticRecallLimit,
                                         double categoryBoost,
                                         double negativePenaltyWeight) {
        this(catalogCandidateLimit, selectedLimit, maxExplicitSkills,
                minRelevanceScore, similarSuppressionThreshold, semanticWeight,
                semanticRecallLimit, categoryBoost, negativePenaltyWeight,
                true, 10, 0.35D);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
