package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangePackageCleanupPolicyTest {

    private final ChangePackageCleanupPolicy policy = new ChangePackageCleanupPolicy();

    @Test
    void directWorkspaceBindingHasHighestPriority() {
        ChangePackageCurrent current = current(Map.of(
                "repairWorkspaceId", "workspace-direct",
                "cleanupPlanJson", "{\"repairWorkspaceId\":\"workspace-plan\"}",
                "evidenceJson", "{\"workspaceId\":\"workspace-evidence\"}"));

        assertEquals("workspace-direct", policy.boundWorkspaceId(current));
    }

    @Test
    void cleanupPlanPrecedesEvidenceFallback() {
        ChangePackageCurrent current = current(Map.of(
                "cleanupPlanJson", "{\"workspaceId\":\"workspace-plan\"}",
                "evidenceJson", "{\"repairWorkspaceId\":\"workspace-evidence\"}"));

        assertEquals("workspace-plan", policy.boundWorkspaceId(current));
    }

    @Test
    void evidenceProvidesLegacyFallback() {
        ChangePackageCurrent current = current(Map.of(
                "cleanupPlanJson", "{}",
                "evidenceJson", "{\"repairWorkspaceId\":\"workspace-evidence\"}"));

        assertEquals("workspace-evidence", policy.boundWorkspaceId(current));
    }

    @Test
    void missingBindingReturnsBlankAndMalformedPlanFailsClosed() {
        assertEquals("", policy.boundWorkspaceId(current(Map.of())));
        assertThrows(RuntimeException.class, () -> policy.boundWorkspaceId(current(Map.of(
                "cleanupPlanJson", "not-json"))));
    }

    private ChangePackageCurrent current(Map<String, Object> values) {
        Map<String, Object> state = new LinkedHashMap<>(values);
        state.put("riskLevel", "MEDIUM");
        return new ChangePackageCurrent(
                1L,
                new ChangePackagePointer("cp-1", ChangePackageStatus.CLOSED, 1, "hash-1", 0, ""),
                "session-1",
                "incident-1",
                "project-1",
                "agent-1",
                1,
                ChangePackageType.GIT_BRANCH_REPAIR,
                ChangePackageCurrentState.fromSnapshot(state),
                null,
                "",
                "creator",
                "",
                null,
                null,
                null);
    }
}
