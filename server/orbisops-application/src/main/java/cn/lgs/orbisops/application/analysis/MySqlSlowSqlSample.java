package cn.lgs.orbisops.application.analysis;

/** Typed MySQL slow SQL evidence independent from Trigger DTOs and JDBC rows. */
public record MySqlSlowSqlSample(
        String startTime,
        String databaseName,
        String userHost,
        String digest,
        String sqlText,
        Double queryTimeMs,
        Long rowsExamined,
        Long rowsSent,
        Long countStar) {

    public MySqlSlowSqlSample {
        startTime = text(startTime);
        databaseName = text(databaseName);
        userHost = text(userHost);
        digest = text(digest);
        sqlText = text(sqlText);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
