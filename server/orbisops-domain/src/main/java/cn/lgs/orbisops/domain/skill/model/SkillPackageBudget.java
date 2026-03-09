package cn.lgs.orbisops.domain.skill.model;

public record SkillPackageBudget(
        int maxTokens,
        int maxOptionalModules,
        int maxArtifacts,
        boolean evaluationMode
) {

    public SkillPackageBudget {
        if (maxTokens <= 0 || maxOptionalModules < 0 || maxArtifacts < 0) {
            throw new IllegalArgumentException("SKILL_PACKAGE_BUDGET_INVALID");
        }
    }
}
