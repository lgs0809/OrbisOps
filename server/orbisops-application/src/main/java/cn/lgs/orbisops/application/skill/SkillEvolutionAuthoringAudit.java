package cn.lgs.orbisops.application.skill;

public record SkillEvolutionAuthoringAudit(
        String modelId,
        String promptVersion,
        long seed,
        String inputHash,
        int candidateBudget,
        int candidateIndex,
        SkillAuthoringDirection direction
) {

    public SkillEvolutionAuthoringAudit {
        modelId = required(modelId, "SKILL_AUTHORING_MODEL_REQUIRED");
        promptVersion = required(promptVersion, "SKILL_AUTHORING_PROMPT_VERSION_REQUIRED");
        inputHash = required(inputHash, "SKILL_AUTHORING_INPUT_HASH_REQUIRED");
        if (candidateBudget < 1 || candidateBudget > 4) {
            throw new IllegalArgumentException("SKILL_AUTHORING_CANDIDATE_BUDGET_INVALID");
        }
        if (candidateIndex < 0 || candidateIndex >= candidateBudget) {
            throw new IllegalArgumentException("SKILL_AUTHORING_CANDIDATE_INDEX_INVALID");
        }
        if (direction == null) throw new IllegalArgumentException("SKILL_AUTHORING_DIRECTION_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
