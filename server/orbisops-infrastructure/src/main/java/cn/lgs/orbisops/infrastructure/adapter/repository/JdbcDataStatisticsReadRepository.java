package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.statistics.adapter.repository.IDataStatisticsReadRepository;
import cn.lgs.orbisops.domain.statistics.model.ExecutionStatisticsFacts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class JdbcDataStatisticsReadRepository implements IDataStatisticsReadRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public JdbcDataStatisticsReadRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public ExecutionStatisticsFacts loadExecutionFacts() {
        return new ExecutionStatisticsFacts(
                count("ai_ops_agent_run",
                        "SELECT COUNT(1) FROM ai_ops_agent_run WHERE create_time >= CURRENT_DATE"),
                count("ai_ops_chat_message",
                        "SELECT COUNT(1) FROM ai_ops_chat_message WHERE role = 'USER' AND create_time >= CURRENT_DATE"),
                count("ai_agent_task_execution",
                        "SELECT COUNT(1) FROM ai_agent_task_execution WHERE started_at >= CURRENT_DATE"),
                count("ai_ops_agent_run",
                        "SELECT COUNT(1) FROM ai_ops_agent_run WHERE status IN ('PENDING','RUNNING')"),
                count("ai_agent_task_execution",
                        "SELECT COUNT(1) FROM ai_agent_task_execution WHERE status = 'RUNNING'"),
                count("ai_ops_agent_audit",
                        "SELECT COUNT(1) FROM ai_ops_agent_audit"),
                count("ai_ops_agent_audit",
                        "SELECT COUNT(1) FROM ai_ops_agent_audit WHERE success = 1"),
                count("ai_ops_agent_run",
                        "SELECT COUNT(1) FROM ai_ops_agent_run WHERE status IN ('SUCCEEDED','FAILED','CANCELED')"),
                count("ai_ops_agent_run",
                        "SELECT COUNT(1) FROM ai_ops_agent_run WHERE status = 'SUCCEEDED'"),
                count("ai_agent_task_execution",
                        "SELECT COUNT(1) FROM ai_agent_task_execution WHERE status IN ('SUCCESS','FAILED')"),
                count("ai_agent_task_execution",
                        "SELECT COUNT(1) FROM ai_agent_task_execution WHERE status = 'SUCCESS'"));
    }

    private long count(String tableName, String sql) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null || !tableExists(jdbcTemplate, tableName)) return 0L;
        try {
            Number value = jdbcTemplate.queryForObject(sql, Number.class);
            return value == null ? 0L : value.longValue();
        } catch (RuntimeException exception) {
            log.debug("统计 SQL 执行失败 table={}", tableName, exception);
            return 0L;
        }
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                            SELECT COUNT(1)
                            FROM information_schema.tables
                            WHERE table_schema = DATABASE()
                              AND table_name = ?
                            """,
                    Integer.class,
                    tableName);
            return count != null && count > 0;
        } catch (RuntimeException exception) {
            log.debug("统计表存在性检查失败 table={}", tableName, exception);
            return false;
        }
    }
}
