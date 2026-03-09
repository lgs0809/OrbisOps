package cn.lgs.orbisops.domain.statistics.model;

public record ExecutionStatisticsSummary(
        long todayRequestCount,
        double successRate,
        long runningTaskCount,
        String successRateSource) {

    public ExecutionStatisticsSummary {
        todayRequestCount = Math.max(0L, todayRequestCount);
        successRate = Math.max(0D, Math.min(100D, successRate));
        runningTaskCount = Math.max(0L, runningTaskCount);
        successRateSource = successRateSource == null ? "NONE" : successRateSource.trim();
    }
}
