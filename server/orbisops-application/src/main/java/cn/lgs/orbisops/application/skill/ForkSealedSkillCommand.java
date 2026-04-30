package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.service.SkillCatalogFingerprint;

public record ForkSealedSkillCommand(SkillGovernanceCommand source,
                                     String targetSkillId,
                                     String targetName) {
    public ForkSealedSkillCommand {
        if (source == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_COMMAND_REQUIRED");
        source.requireApproval();
        targetSkillId = SkillCatalogFingerprint.normalizeId(text(targetSkillId));
        if (targetSkillId.isBlank()) throw new IllegalArgumentException("SKILL_FORK_TARGET_ID_REQUIRED");
        targetName = text(targetName);
        if (targetName.isBlank()) targetName = targetSkillId;
        if (targetSkillId.equals(source.skillId())) {
            throw new IllegalArgumentException("SKILL_FORK_TARGET_MUST_DIFFER");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
