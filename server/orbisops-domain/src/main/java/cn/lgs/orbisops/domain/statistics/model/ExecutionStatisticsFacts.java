package cn.lgs.orbisops.domain.statistics.model;

public record ExecutionStatisticsFacts(
        long todayRunRequests,
        long todayChatUserRequests,
        long todayLegacyTaskRequests,
        long runningRuns,
        long runningLegacyTasks,
        long auditTotal,
        long auditSuccess,
        long terminalRuns,
        long successfulRuns,
        long terminalLegacyTasks,
        long successfulLegacyTasks) {

    public ExecutionStatisticsFacts {
        todayRunRequests = nonNegative(todayRunRequests);
        todayChatUserRequests = nonNegative(todayChatUserRequests);
        todayLegacyTaskRequests = nonNegative(todayLegacyTaskRequests);
        runningRuns = nonNegative(runningRuns);
        runningLegacyTasks = nonNegative(runningLegacyTasks);
        auditTotal = nonNegative(auditTotal);
        auditSuccess = bounded(auditSuccess, auditTotal);
        terminalRuns = nonNegative(terminalRuns);
        successfulRuns = bounded(successfulRuns, terminalRuns);
        terminalLegacyTasks = nonNegative(terminalLegacyTasks);
        successfulLegacyTasks = bounded(successfulLegacyTasks, terminalLegacyTasks);
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private static long bounded(long value, long total) {
        return Math.min(nonNegative(value), total);
    }
}
