package cn.lgs.orbisops.domain.statistics.service;

import cn.lgs.orbisops.domain.statistics.model.ExecutionStatisticsFacts;
import cn.lgs.orbisops.domain.statistics.model.ExecutionStatisticsSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DataStatisticsPolicy {

    public ExecutionStatisticsSummary summarize(ExecutionStatisticsFacts facts) {
        if (facts == null) throw new IllegalArgumentException("EXECUTION_STATISTICS_FACTS_REQUIRED");
        long today = facts.todayRunRequests()
                + facts.todayChatUserRequests()
                + facts.todayLegacyTaskRequests();
        long running = facts.runningRuns() + facts.runningLegacyTasks();
        if (facts.auditTotal() > 0) {
            return new ExecutionStatisticsSummary(
                    today, percentage(facts.auditSuccess(), facts.auditTotal()), running, "AUDIT");
        }
        if (facts.terminalRuns() > 0) {
            return new ExecutionStatisticsSummary(
                    today, percentage(facts.successfulRuns(), facts.terminalRuns()), running, "RUN");
        }
        if (facts.terminalLegacyTasks() > 0) {
            return new ExecutionStatisticsSummary(
                    today,
                    percentage(facts.successfulLegacyTasks(), facts.terminalLegacyTasks()),
                    running,
                    "LEGACY_TASK");
        }
        return new ExecutionStatisticsSummary(today, 0D, running, "NONE");
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0) return 0D;
        return BigDecimal.valueOf(numerator * 100D / denominator)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
