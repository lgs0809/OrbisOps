package cn.lgs.orbisops.application.analysis;

import java.util.List;

/** Result of the MySQL slow SQL evidence query, including fallback provenance. */
public record MySqlSlowSqlQueryResult(
        boolean available,
        String sourceName,
        String message,
        String queryDescription,
        List<MySqlSlowSqlSample> samples) {

    public MySqlSlowSqlQueryResult {
        sourceName = text(sourceName);
        message = text(message);
        queryDescription = text(queryDescription);
        samples = samples == null ? List.of() : List.copyOf(samples);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
