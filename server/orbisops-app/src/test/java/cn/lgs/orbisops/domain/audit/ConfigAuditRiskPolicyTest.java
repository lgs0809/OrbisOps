package cn.lgs.orbisops.domain.audit;

import cn.lgs.orbisops.domain.audit.service.ConfigAuditRiskPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigAuditRiskPolicyTest {

    private final ConfigAuditRiskPolicy policy = new ConfigAuditRiskPolicy();

    @Test
    void configuredRiskWinsAndIsNormalized() {
        assertEquals("CRITICAL", policy.resolve("skill", "update", " critical "));
    }

    @Test
    void destructiveAndPermissionActionsAreHighRisk() {
        assertEquals("HIGH", policy.resolve("agent-definition", "rollback", ""));
        assertEquals("HIGH", policy.resolve("admin-role", "update-permission", null));
        assertEquals("HIGH", policy.resolve("credential", "create", ""));
    }

    @Test
    void mutationsAreMediumAndReadsAreLow() {
        assertEquals("MEDIUM", policy.resolve("skill", "publish", ""));
        assertEquals("MEDIUM", policy.resolve("task", "dry-run", ""));
        assertEquals("LOW", policy.resolve("audit", "search", ""));
    }
}
