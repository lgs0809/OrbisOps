package cn.lgs.orbisops.domain.investigation.service;

import cn.lgs.orbisops.domain.investigation.model.InvestigationRetryAdjustment;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationRetryPolicyTest {

    private final InvestigationRetryPolicy policy = new InvestigationRetryPolicy();

    @Test
    void notRequestedDoesNotReadMissingRange() {
        Optional<InvestigationRetryAdjustment> adjustment = policy.adjust(
                new InvestigationRetryPolicy.Input(
                        "elasticsearch",
                        "INSUFFICIENT",
                        false,
                        null,
                        "5m"));

        assertTrue(adjustment.isEmpty());
    }

    @Test
    void onlyExactLegacyStatusesAreRetryable() {
        assertTrue(adjust("elasticsearch", "FOUND", true, 10, "5m").isEmpty());
        assertTrue(adjust("elasticsearch", "insufficient", true, 10, "5m").isEmpty());
        assertTrue(adjust("elasticsearch", " INSUFFICIENT ", true, 10, "5m").isEmpty());
        assertTrue(adjust("elasticsearch", "NOT_FOUND", true, 10, "5m").isPresent());
        assertTrue(adjust("elasticsearch", "INSUFFICIENT", true, 10, "5m").isPresent());
    }

    @Test
    void expandsRangeByMaxOfFourTimesOrPlusFifteen() {
        assertEquals(18, adjust("elasticsearch", "INSUFFICIENT", true, 3, "5m")
                .orElseThrow()
                .rangeMinutes());
        assertEquals(40, adjust("elasticsearch", "INSUFFICIENT", true, 10, "5m")
                .orElseThrow()
                .rangeMinutes());
    }

    @Test
    void capsExpandedRangeAtTwoHundredFortyMinutes() {
        assertEquals(240, adjust("elasticsearch", "NOT_FOUND", true, 100, "5m")
                .orElseThrow()
                .rangeMinutes());
    }

    @Test
    void expandsOnlyPrometheusFiveMinuteWindow() {
        InvestigationRetryAdjustment prometheus = adjust(
                "prometheus",
                "INSUFFICIENT",
                true,
                10,
                "5m").orElseThrow();
        InvestigationRetryAdjustment elasticsearch = adjust(
                "elasticsearch",
                "INSUFFICIENT",
                true,
                10,
                "5m").orElseThrow();
        InvestigationRetryAdjustment prometheusFifteen = adjust(
                "prometheus",
                "INSUFFICIENT",
                true,
                10,
                "15m").orElseThrow();

        assertEquals("15m", prometheus.promWindow());
        assertEquals("5m", elasticsearch.promWindow());
        assertEquals("15m", prometheusFifteen.promWindow());
    }

    @Test
    void preservesExactSourceAndPromWindowComparison() {
        InvestigationRetryAdjustment paddedSource = adjust(
                " prometheus ",
                "INSUFFICIENT",
                true,
                10,
                "5m").orElseThrow();
        InvestigationRetryAdjustment paddedWindow = adjust(
                "prometheus",
                "INSUFFICIENT",
                true,
                10,
                "5m ").orElseThrow();

        assertEquals("5m", paddedSource.promWindow());
        assertEquals("5m ", paddedWindow.promWindow());
    }

    @Test
    void preservesNullPromWindowWhenNoWindowExpansionApplies() {
        InvestigationRetryAdjustment elasticsearch = adjust(
                "elasticsearch",
                "INSUFFICIENT",
                true,
                10,
                null).orElseThrow();
        InvestigationRetryAdjustment prometheus = adjust(
                "prometheus",
                "INSUFFICIENT",
                true,
                10,
                null).orElseThrow();

        assertNull(elasticsearch.promWindow());
        assertNull(prometheus.promWindow());
    }

    @Test
    void eligibleRetryWithMissingRangePreservesLegacyFailure() {
        assertThrows(
                NullPointerException.class,
                () -> adjust("prometheus", "INSUFFICIENT", true, null, "5m"));
    }

    private Optional<InvestigationRetryAdjustment> adjust(
            String source,
            String status,
            boolean requested,
            Integer rangeMinutes,
            String promWindow) {
        return policy.adjust(new InvestigationRetryPolicy.Input(
                source,
                status,
                requested,
                rangeMinutes,
                promWindow));
    }
}
