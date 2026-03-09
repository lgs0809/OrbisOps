package cn.lgs.orbisops.domain.statistics.model;

public record DataStatisticsSnapshot(
        long activeAgentCount,
        long mcpToolCount,
        long ragOrderCount,
        long modelCount,
        long todayRequestCount,
        double successRate,
        long runningTaskCount) {

    public DataStatisticsSnapshot {
        activeAgentCount = Math.max(0L, activeAgentCount);
        mcpToolCount = Math.max(0L, mcpToolCount);
        ragOrderCount = Math.max(0L, ragOrderCount);
        modelCount = Math.max(0L, modelCount);
        todayRequestCount = Math.max(0L, todayRequestCount);
        successRate = Math.max(0D, Math.min(100D, successRate));
        runningTaskCount = Math.max(0L, runningTaskCount);
    }
}
