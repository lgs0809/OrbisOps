package cn.lgs.orbisops.trigger.ops;

/** Typed and bounded settings for the MySQL slow SQL sub-agent. */
public record OpsMySqlSlowSqlSettings(
        boolean enabled,
        int sampleSize,
        double thresholdMs,
        boolean performanceSchemaFallback) {

    public OpsMySqlSlowSqlSettings {
        sampleSize = Math.max(1, Math.min(sampleSize, 50));
        thresholdMs = Math.max(0D, thresholdMs);
    }

    public static OpsMySqlSlowSqlSettings defaults() {
        return new OpsMySqlSlowSqlSettings(true, 10, 500D, true);
    }
}
