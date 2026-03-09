package cn.lgs.orbisops.domain.alert;

import cn.lgs.orbisops.domain.alert.model.AlertEventSnapshot;
import cn.lgs.orbisops.domain.alert.model.AlertRunOutcome;
import cn.lgs.orbisops.domain.alert.service.AlertEventPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AlertEventPolicyTest {

    private final AlertEventPolicy policy = new AlertEventPolicy();

    @Test
    void clampsListLimit() {
        assertEquals(1, policy.limit(0));
        assertEquals(50, policy.limit(50));
        assertEquals(200, policy.limit(999));
    }

    @Test
    void recognizesTerminalRunStatuses() {
        assertTrue(policy.terminal("SUCCEEDED"));
        assertTrue(policy.terminal(" failed "));
        assertTrue(policy.terminal("CANCELED"));
        assertFalse(policy.terminal("RUNNING"));
        assertFalse(policy.terminal(null));
    }

    @Test
    void supportsOnlyAlertmanagerOutcomesWithFingerprint() {
        assertTrue(policy.supports(new AlertRunOutcome(
                "alertmanager", "fingerprint-1", "run-1", "SUCCEEDED", "ok", "")));
        assertFalse(policy.supports(new AlertRunOutcome(
                "MANUAL", "fingerprint-1", "run-1", "SUCCEEDED", "ok", "")));
        assertFalse(policy.supports(new AlertRunOutcome(
                "ALERTMANAGER", "", "run-1", "SUCCEEDED", "ok", "")));
        assertFalse(policy.supports(null));
    }

    @Test
    void identifiesIncidentEligibleEventStatuses() {
        assertTrue(policy.incidentEligible(event("TRIGGERED")));
        assertTrue(policy.incidentEligible(event("QUEUED")));
        assertTrue(policy.incidentEligible(event("FAILED")));
        assertTrue(policy.incidentEligible(event("DEDUPED")));
        assertTrue(policy.incidentEligible(event("RECOVERY_TRIGGERED")));
        assertFalse(policy.incidentEligible(event("REJECTED")));
        assertFalse(policy.incidentEligible(null));
    }

    private AlertEventSnapshot event(String status) {
        return new AlertEventSnapshot(
                1L, 2L, "rule", "project", "ALERTMANAGER", status,
                "dispatch", "fingerprint", "HighErrorRate", "critical", "payment", "ops",
                "run-1", "PENDING", "", "", "", Map.of(), Map.of(), Map.of(), "now");
    }
}
