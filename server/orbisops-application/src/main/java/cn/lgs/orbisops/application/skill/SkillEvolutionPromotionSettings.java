package cn.lgs.orbisops.application.skill;

/** Bounded repetition thresholds for promoting observations into authored Skill candidates. */
public record SkillEvolutionPromotionSettings(
        int minimumObservations,
        int explicitProcedureMinimumObservations,
        int minimumDistinctRuns,
        int minimumDistinctSessions,
        int consolidationSampleLimit) {

    public SkillEvolutionPromotionSettings(
            int minimumObservations,
            int explicitProcedureMinimumObservations) {
        this(
                minimumObservations,
                explicitProcedureMinimumObservations,
                2,
                2,
                12);
    }

    public SkillEvolutionPromotionSettings {
        minimumObservations = Math.max(3, minimumObservations);
        explicitProcedureMinimumObservations = Math.max(
                3,
                explicitProcedureMinimumObservations);
        minimumDistinctRuns = Math.max(3, minimumDistinctRuns);
        minimumDistinctSessions = Math.max(2, minimumDistinctSessions);
        consolidationSampleLimit = Math.max(
                3,
                Math.min(20, consolidationSampleLimit));
    }

    public int requiredObservations(String opportunityType) {
        return "USER_ASSERTED_PROCEDURE".equals(opportunityType)
                ? explicitProcedureMinimumObservations
                : minimumObservations;
    }

    public boolean sourceDiversitySatisfied(
            int distinctRuns,
            int distinctSessions) {
        return distinctRuns >= minimumDistinctRuns
                && distinctSessions >= minimumDistinctSessions;
    }
}
