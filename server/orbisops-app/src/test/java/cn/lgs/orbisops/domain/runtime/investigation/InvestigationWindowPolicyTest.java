package cn.lgs.orbisops.domain.runtime.investigation;

import cn.lgs.orbisops.domain.runtime.investigation.model.InvestigationWindow;
import cn.lgs.orbisops.domain.runtime.investigation.service.InvestigationWindowPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationWindowPolicyTest {

    private final InvestigationWindowPolicy policy = new InvestigationWindowPolicy();

    @Test
    void rangeDecisionCanOnlyExpand() {
        InvestigationWindow current = new InvestigationWindow(30, "5m", false);

        InvestigationWindow smaller = policy.applyLogDecision(current, 10, true);
        InvestigationWindow larger = policy.applyLogDecision(current, 60, null);

        assertEquals(30, smaller.rangeMinutes());
        assertTrue(smaller.includeRecentLogs());
        assertEquals(60, larger.rangeMinutes());
        assertFalse(larger.includeRecentLogs());
    }

    @Test
    void retriesExpandRangeButNeverExceedFourHours() {
        InvestigationWindow expanded = policy.expandRange(
                new InvestigationWindow(15, "", true),
                true);
        assertEquals(30, expanded.rangeMinutes());

        InvestigationWindow capped = policy.expandRange(
                new InvestigationWindow(200, "", false),
                false);
        assertEquals(240, capped.rangeMinutes());
    }

    @Test
    void prometheusWindowNeverShrinksAndEventuallyCapsAtOneHour() {
        InvestigationWindow current = new InvestigationWindow(15, "15m", null);

        assertEquals(
                "15m",
                policy.applyPrometheusWindowDecision(current, "5m")
                        .prometheusWindow());
        assertEquals(
                "30m",
                policy.applyPrometheusWindowDecision(current, "30m")
                        .prometheusWindow());
        assertEquals(
                "30m",
                policy.expandPrometheusWindow(current).prometheusWindow());
        assertEquals(
                "1h",
                policy.expandPrometheusWindow(
                        new InvestigationWindow(15, "1h", null))
                        .prometheusWindow());
    }

    @Test
    void missingWindowUsesBoundedDefaults() {
        InvestigationWindow safe = policy.safe(null);

        assertEquals(15, safe.rangeMinutes());
        assertEquals("", safe.prometheusWindow());
    }
}
