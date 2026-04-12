package cn.lgs.orbisops.infrastructure.adapter.analysis;

import cn.lgs.orbisops.application.analysis.MySqlSlowSqlQuery;
import cn.lgs.orbisops.application.analysis.MySqlSlowSqlQueryPort;
import cn.lgs.orbisops.application.analysis.MySqlSlowSqlQueryResult;
import cn.lgs.orbisops.application.analysis.MySqlSlowSqlSample;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC collector for MySQL slow_log with performance_schema fallback. */
@Repository
public class JdbcMySqlSlowSqlQueryAdapter implements MySqlSlowSqlQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMySqlSlowSqlQueryAdapter(
            @Qualifier("mysqlJdbcTemplate")
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public MySqlSlowSqlQueryResult query(MySqlSlowSqlQuery query) {
        if (jdbcTemplate == null) {
            return new MySqlSlowSqlQueryResult(
                    false,
                    "mysql.slow_log/performance_schema",
                    "MySQL 数据源未配置，无法查询慢 SQL。",
                    "mysqlJdbcTemplate unavailable",
                    List.of());
        }
        try {
            return querySlowLog(query);
        } catch (DataAccessException slowLogError) {
            if (!query.performanceSchemaFallback()) {
                return new MySqlSlowSqlQueryResult(
                        false,
                        "mysql.slow_log",
                        "mysql.slow_log 不可用：" + rootMessage(slowLogError),
                        "mysql.slow_log range=now-"
                                + query.rangeMinutes() + "m..now",
                        List.of());
            }
            return queryPerformanceSchema(query, slowLogError);
        }
    }

    private MySqlSlowSqlQueryResult querySlowLog(MySqlSlowSqlQuery query) {
        String sql = """
                SELECT DATE_FORMAT(start_time, '%Y-%m-%d %H:%i:%s') AS start_time,
                       db,
                       user_host,
                       sql_text,
                       TIME_TO_SEC(query_time) * 1000 AS query_time_ms,
                       rows_examined,
                       rows_sent
                FROM mysql.slow_log
                WHERE start_time >= DATE_SUB(NOW(), INTERVAL ? MINUTE)
                  AND TIME_TO_SEC(query_time) * 1000 >= ?
                ORDER BY query_time DESC
                LIMIT ?
                """;
        List<MySqlSlowSqlSample> samples = jdbcTemplate.execute(
                (ConnectionCallback<List<MySqlSlowSqlSample>>) connection -> {
                    try (PreparedStatement statement =
                                 connection.prepareStatement(sql)) {
                        statement.setQueryTimeout(query.queryTimeoutSeconds());
                        statement.setInt(1, query.rangeMinutes());
                        statement.setDouble(2, query.thresholdMs());
                        statement.setInt(3, query.sampleSize());
                        try (ResultSet rs = statement.executeQuery()) {
                            List<MySqlSlowSqlSample> values = new ArrayList<>();
                            while (rs.next()) {
                                long rowsExamined = rs.getLong("rows_examined");
                                Long safeRowsExamined = rs.wasNull()
                                        ? null
                                        : rowsExamined;
                                long rowsSent = rs.getLong("rows_sent");
                                Long safeRowsSent = rs.wasNull()
                                        ? null
                                        : rowsSent;
                                values.add(new MySqlSlowSqlSample(
                                        rs.getString("start_time"),
                                        rs.getString("db"),
                                        rs.getString("user_host"),
                                        "",
                                        rs.getString("sql_text"),
                                        round(rs.getDouble("query_time_ms"), 2),
                                        safeRowsExamined,
                                        safeRowsSent,
                                        null));
                            }
                            return values;
                        }
                    }
                });
        return new MySqlSlowSqlQueryResult(
                true,
                "mysql.slow_log",
                "mysql.slow_log 查询成功",
                "mysql.slow_log range=now-" + query.rangeMinutes()
                        + "m..now thresholdMs=" + query.thresholdMs(),
                Optional.ofNullable(samples).orElse(List.of()));
    }

    private MySqlSlowSqlQueryResult queryPerformanceSchema(
            MySqlSlowSqlQuery query,
            DataAccessException slowLogError) {
        try {
            String sql = """
                    SELECT SCHEMA_NAME,
                           DIGEST,
                           DIGEST_TEXT,
                           COUNT_STAR,
                           AVG_TIMER_WAIT / 1000000000 AS avg_query_time_ms,
                           MAX_TIMER_WAIT / 1000000000 AS max_query_time_ms,
                           SUM_ROWS_EXAMINED,
                           SUM_ROWS_SENT
                    FROM performance_schema.events_statements_summary_by_digest
                    WHERE DIGEST_TEXT IS NOT NULL
                    ORDER BY MAX_TIMER_WAIT DESC
                    LIMIT ?
                    """;
            List<MySqlSlowSqlSample> samples = jdbcTemplate.execute(
                    (ConnectionCallback<List<MySqlSlowSqlSample>>) connection -> {
                        try (PreparedStatement statement =
                                     connection.prepareStatement(sql)) {
                            statement.setQueryTimeout(
                                    query.queryTimeoutSeconds());
                            statement.setInt(1, query.sampleSize());
                            try (ResultSet rs = statement.executeQuery()) {
                                List<MySqlSlowSqlSample> values =
                                        new ArrayList<>();
                                while (rs.next()) {
                                    values.add(new MySqlSlowSqlSample(
                                            "",
                                            rs.getString("SCHEMA_NAME"),
                                            "",
                                            rs.getString("DIGEST"),
                                            rs.getString("DIGEST_TEXT"),
                                            round(rs.getDouble(
                                                    "max_query_time_ms"), 2),
                                            longNumber(rs.getObject(
                                                    "SUM_ROWS_EXAMINED")),
                                            longNumber(rs.getObject(
                                                    "SUM_ROWS_SENT")),
                                            longNumber(rs.getObject(
                                                    "COUNT_STAR"))));
                                }
                                return values;
                            }
                        }
                    });
            List<MySqlSlowSqlSample> filtered = Optional
                    .ofNullable(samples)
                    .orElse(List.of())
                    .stream()
                    .filter(sample -> sample.queryTimeMs() != null
                            && sample.queryTimeMs() >= query.thresholdMs())
                    .toList();
            return new MySqlSlowSqlQueryResult(
                    true,
                    "performance_schema.events_statements_summary_by_digest",
                    "mysql.slow_log 不可用，已使用 performance_schema 累计摘要降级查询",
                    "performance_schema.events_statements_summary_by_digest thresholdMs="
                            + query.thresholdMs(),
                    filtered);
        } catch (DataAccessException performanceSchemaError) {
            return new MySqlSlowSqlQueryResult(
                    false,
                    "mysql.slow_log/performance_schema",
                    "mysql.slow_log 与 performance_schema 均不可用："
                            + rootMessage(slowLogError)
                            + "；"
                            + rootMessage(performanceSchemaError),
                    "slow_log + performance_schema",
                    List.of());
        }
    }

    private String rootMessage(DataAccessException error) {
        if (error == null) return "unknown data access error";
        Throwable cause = error.getMostSpecificCause();
        String message = cause == null ? error.getMessage() : cause.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message.trim();
    }

    private Long longNumber(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double round(double value, int scale) {
        double factor = Math.pow(10D, Math.max(0, scale));
        return Math.round(value * factor) / factor;
    }
}
