package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillGovernancePolicyTest {

    @Test
    void frozenAndManualGovernanceControlsAutoUpdate() {
        SkillGovernancePolicy policy = new SkillGovernancePolicy();

        assertTrue(policy.frozen(Map.of("status", "FROZEN", "updateMode", "AUTO")));
        assertTrue(policy.frozen(Map.of("status", "ACTIVE", "updateMode", "FROZEN")));
        assertFalse(policy.canAutoUpdate(Map.of(
                "status", "FROZEN", "updateMode", "AUTO", "autoUpdateEnabled", true)));
        assertFalse(policy.canAutoUpdate(Map.of(
                "status", "ACTIVE", "updateMode", "FROZEN", "autoUpdateEnabled", true)));
        assertFalse(policy.canAutoUpdate(Map.of(
                "status", "ACTIVE", "updateMode", "AUTO", "autoUpdateEnabled", false)));
        assertTrue(policy.canAutoUpdate(Map.of(
                "status", "ACTIVE", "updateMode", "AUTO", "autoUpdateEnabled", true)));
    }

    @Test
    void typedRollbackKeepsLegacyUnknownGovernanceFallback() {
        SkillCatalogEntry legacy = new SkillCatalogEntry(
                1L, "slow-sql", "", "Slow SQL", "GLOBAL", "",
                "desc", "content", 3, "UNKNOWN", "owner", null, null,
                "MANUAL", "UNKNOWN", true, true, null,
                "", "", null, "hash-3", 3, "hash-3", 3,
                "package-3", "{}", "{}");

        assertDoesNotThrow(() -> new SkillGovernancePolicy().requireRollback(legacy, 2));
    }
}
