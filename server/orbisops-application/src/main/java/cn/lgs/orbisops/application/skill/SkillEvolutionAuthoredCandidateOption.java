package cn.lgs.orbisops.application.skill;

public record SkillEvolutionAuthoredCandidateOption(
        SkillEvolutionAuthoredCandidate candidate,
        SkillEvolutionAuthoringAudit audit
) {

    public SkillEvolutionAuthoredCandidateOption {
        if (candidate == null || audit == null) {
            throw new IllegalArgumentException("SKILL_AUTHORING_CANDIDATE_OPTION_REQUIRED");
        }
    }
}
