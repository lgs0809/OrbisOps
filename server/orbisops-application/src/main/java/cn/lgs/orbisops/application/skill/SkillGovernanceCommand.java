package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.service.SkillCatalogFingerprint;

import java.util.Locale;
import java.util.Set;

public record SkillGovernanceCommand(String scope,
                                     String projectId,
                                     String skillId,
                                     int expectedVersion,
                                     String expectedSkillHash,
                                     String reason,
                                     String approvalId,
                                     String actor) {

    public SkillGovernanceCommand {
        scope = required(scope, "SKILL_GOVERNANCE_SCOPE_REQUIRED").toUpperCase(Locale.ROOT);
        if (!Set.of("GLOBAL", "PROJECT").contains(scope)) {
            throw new IllegalArgumentException("SKILL_GOVERNANCE_SCOPE_INVALID:" + scope);
        }
        projectId = text(projectId);
        if ("PROJECT".equals(scope) && projectId.isBlank()) {
            throw new IllegalArgumentException("SKILL_PROJECT_ID_REQUIRED");
        }
        if ("GLOBAL".equals(scope)) projectId = "";
        skillId = SkillCatalogFingerprint.normalizeId(required(skillId, "SKILL_ID_REQUIRED"));
        if (skillId.isBlank()) throw new IllegalArgumentException("SKILL_ID_REQUIRED");
        if (expectedVersion <= 0) throw new IllegalArgumentException("SKILL_EXPECTED_VERSION_REQUIRED");
        expectedSkillHash = required(expectedSkillHash, "SKILL_EXPECTED_HASH_REQUIRED");
        reason = required(reason, "SKILL_GOVERNANCE_REASON_REQUIRED");
        approvalId = text(approvalId);
        actor = required(actor, "SKILL_ACTOR_REQUIRED");
    }

    public void requireApproval() {
        if (approvalId.isBlank()) throw new IllegalArgumentException("SKILL_GOVERNANCE_APPROVAL_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
