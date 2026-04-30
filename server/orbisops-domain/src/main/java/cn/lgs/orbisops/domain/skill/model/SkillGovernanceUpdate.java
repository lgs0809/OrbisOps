package cn.lgs.orbisops.domain.skill.model;

import java.util.Locale;
import java.util.Set;

/** Catalog-only governance transition guarded by the immutable package pointer CAS. */
public record SkillGovernanceUpdate(String scope,
                                    String projectId,
                                    String skillId,
                                    int expectedVersion,
                                    String expectedSkillHash,
                                    SkillGovernanceState expectedState,
                                    SkillGovernanceState nextState) {

    public SkillGovernanceUpdate {
        scope = required(scope, "SKILL_GOVERNANCE_SCOPE_REQUIRED").toUpperCase(Locale.ROOT);
        if (!Set.of("GLOBAL", "PROJECT").contains(scope)) {
            throw new IllegalArgumentException("SKILL_GOVERNANCE_SCOPE_INVALID:" + scope);
        }
        projectId = text(projectId);
        if ("PROJECT".equals(scope) && projectId.isBlank()) {
            throw new IllegalArgumentException("SKILL_GOVERNANCE_PROJECT_REQUIRED");
        }
        if ("GLOBAL".equals(scope)) projectId = "";
        skillId = required(skillId, "SKILL_GOVERNANCE_ID_REQUIRED");
        if (expectedVersion <= 0) throw new IllegalArgumentException("SKILL_GOVERNANCE_VERSION_REQUIRED");
        expectedSkillHash = required(expectedSkillHash, "SKILL_GOVERNANCE_HASH_REQUIRED");
        if (expectedState == null) {
            throw new IllegalArgumentException("SKILL_GOVERNANCE_EXPECTED_STATE_REQUIRED");
        }
        if (nextState == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_STATE_REQUIRED");
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
