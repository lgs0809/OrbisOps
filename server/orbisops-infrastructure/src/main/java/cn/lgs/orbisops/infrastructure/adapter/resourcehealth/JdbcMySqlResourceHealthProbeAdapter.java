package cn.lgs.orbisops.infrastructure.adapter.resourcehealth;

import cn.lgs.orbisops.application.resourcehealth.MySqlResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthCheck;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;

/** JDBC probe for MySQL connectivity and slow SQL evidence sources. */
@Repository
public class JdbcMySqlResourceHealthProbeAdapter
        implements MySqlResourceHealthProbePort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMySqlResourceHealthProbeAdapter(
            @Qualifier("mysqlJdbcTemplate")
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public ResourceHealthCheck probe() {
        if (jdbcTemplate == null) {
            return ResourceHealthCheck.unavailable(
                    "mysql_slow_sql",
                    "MySQL 慢 SQL",
                    "jdbc:mysql",
                    "JdbcTemplate 未初始化");
        }
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            boolean slowLog = existsTable("mysql", "slow_log");
            boolean performanceSchema = existsTable(
                    "performance_schema",
                    "events_statements_summary_by_digest");
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("slowLogAvailable", slowLog);
            details.put("performanceSchemaAvailable", performanceSchema);
            boolean healthy = slowLog || performanceSchema;
            return new ResourceHealthCheck(
                    "mysql_slow_sql",
                    "MySQL 慢 SQL",
                    "jdbc:mysql",
                    healthy,
                    healthy
                            ? "连接正常，慢 SQL 资源可查询"
                            : "连接正常，但 mysql.slow_log 和 performance_schema 慢 SQL 摘要均不可用",
                    details);
        } catch (RuntimeException error) {
            return ResourceHealthCheck.unavailable(
                    "mysql_slow_sql",
                    "MySQL 慢 SQL",
                    "jdbc:mysql",
                    message("慢 SQL 资源检查失败", error));
        }
    }

    private boolean existsTable(String schema, String table) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(1)
                        FROM information_schema.tables
                        WHERE table_schema = ?
                          AND table_name = ?
                        """,
                Integer.class,
                schema,
                table);
        return count != null && count > 0;
    }

    private String message(String prefix, RuntimeException error) {
        String message = error.getMessage();
        return prefix + "：" + (message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message.trim());
    }
}
