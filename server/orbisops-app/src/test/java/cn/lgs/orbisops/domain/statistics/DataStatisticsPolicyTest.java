package cn.lgs.orbisops.domain.statistics;

import cn.lgs.orbisops.domain.statistics.model.ExecutionStatisticsFacts;
import cn.lgs.orbisops.domain.statistics.model.ExecutionStatisticsSummary;
import cn.lgs.orbisops.domain.statistics.service.DataStatisticsPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataStatisticsPolicyTest {

    private final DataStatisticsPolicy policy = new DataStatisticsPolicy();

    @Test
    void auditFactsHavePriorityAndAggregateRequestAndRunningCounts() {
        ExecutionStatisticsSummary summary = policy.summarize(new ExecutionStatisticsFacts(
                2, 3, 4,
                5, 6,
                3, 2,
                10, 10,
                20, 20));

        assertEquals(9, summary.todayRequestCount());
        assertEquals(11, summary.runningTaskCount());
        assertEquals(66.67D, summary.successRate());
        assertEquals("AUDIT", summary.successRateSource());
    }

    @Test
    void terminalRunFactsAreUsedWhenAuditHasNoSamples() {
        ExecutionStatisticsSummary summary = policy.summarize(new ExecutionStatisticsFacts(
                0, 0, 0,
                0, 0,
                0, 0,
                3, 1,
                10, 10));

        assertEquals(33.33D, summary.successRate());
        assertEquals("RUN", summary.successRateSource());
    }

    @Test
    void legacyTaskFactsAreTheFinalSuccessRateFallback() {
        ExecutionStatisticsSummary summary = policy.summarize(new ExecutionStatisticsFacts(
                0, 0, 0,
                0, 0,
                0, 0,
                0, 0,
                4, 2));

        assertEquals(50D, summary.successRate());
        assertEquals("LEGACY_TASK", summary.successRateSource());
    }

    @Test
    void factsClampNegativeCountsAndSuccessesBeyondTotals() {
        ExecutionStatisticsSummary summary = policy.summarize(new ExecutionStatisticsFacts(
                -1, -2, -3,
                -4, -5,
                2, 9,
                0, 8,
                0, 7));

        assertEquals(0, summary.todayRequestCount());
        assertEquals(0, summary.runningTaskCount());
        assertEquals(100D, summary.successRate());
        assertEquals("AUDIT", summary.successRateSource());
    }

    @Test
    void missingFactsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> policy.summarize(null));
    }
}
