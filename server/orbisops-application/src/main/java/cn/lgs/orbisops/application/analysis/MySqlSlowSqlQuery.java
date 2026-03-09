package cn.lgs.orbisops.application.analysis;

/** Bounded query for collecting MySQL slow SQL evidence. */
public record MySqlSlowSqlQuery(
        int rangeMinutes,
        double thresholdMs,
        int sampleSize,
        int queryTimeoutSeconds,
        boolean performanceSchemaFallback) {

    public MySqlSlowSqlQuery {
        rangeMinutes = Math.max(1, Math.min(rangeMinutes, 1440));
        thresholdMs = Math.max(0D, thresholdMs);
        sampleSize = Math.max(1, Math.min(sampleSize, 50));
        queryTimeoutSeconds = Math.max(1, Math.min(queryTimeoutSeconds, 300));
    }
}
