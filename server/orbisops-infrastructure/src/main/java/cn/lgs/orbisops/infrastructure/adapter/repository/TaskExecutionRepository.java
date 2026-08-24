package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCatalogPort;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCommand;
import cn.lgs.orbisops.application.schedule.TaskExecutionCatalogPort;
import cn.lgs.orbisops.application.schedule.TaskExecutionView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

/**
 * JDBC adapter for scheduled task execution lifecycle and history.
 *
 * <p>The persistence adapter implements the typed Application ports directly. Database rows never
 * leak into Domain/Application as compatibility records.</p>
 */
@Repository
public class TaskExecutionRepository implements ScheduledTaskExecutionCatalogPort, TaskExecutionCatalogPort {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public TaskExecutionRepository(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public void ensureStorage() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        if (jdbcTemplate == null) {
            return;
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_agent_task_execution (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  schedule_id BIGINT NOT NULL COMMENT '任务配置ID',
                  task_name VARCHAR(255) DEFAULT NULL COMMENT '任务名称',
                  agent_id VARCHAR(32) NOT NULL COMMENT '智能体ID',
                  trigger_type VARCHAR(32) NOT NULL COMMENT '触发方式',
                  status VARCHAR(16) NOT NULL COMMENT '执行状态',
                  started_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
                  ended_at DATETIME DEFAULT NULL COMMENT '结束时间',
                  input LONGTEXT COMMENT '执行输入',
                  output LONGTEXT COMMENT '执行输出',
                  error_message TEXT COMMENT '错误信息',
                  PRIMARY KEY (id),
                  KEY idx_schedule_id (schedule_id),
                  KEY idx_started_at (started_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能体周期任务执行记录表'
                """);
        jdbcTemplate.execute(
                "ALTER TABLE ai_agent_task_execution MODIFY COLUMN agent_id VARCHAR(64) NOT NULL COMMENT 'Agent Definition ID'");
        jdbcTemplate.execute(
                "ALTER TABLE ai_agent_task_execution MODIFY COLUMN task_name VARCHAR(255) DEFAULT NULL COMMENT '任务名称'");
    }

    @Override
    public Long create(ScheduledTaskExecutionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("SCHEDULED_TASK_EXECUTION_COMMAND_REQUIRED");
        }
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        if (jdbcTemplate == null) {
            return 0L;
        }
        if (!StringUtils.hasText(command.agentId())) {
            throw new IllegalArgumentException("执行 Agent 不能为空");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO ai_agent_task_execution
                    (schedule_id, task_name, agent_id, trigger_type, status, started_at)
                    VALUES (?, ?, ?, ?, 'RUNNING', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, command.scheduleId());
            ps.setString(2, abbreviate(command.taskName(), 255));
            ps.setString(3, command.agentId().trim());
            ps.setString(4, command.triggerType());
            ps.setObject(5, LocalDateTime.now());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建任务执行记录失败");
        }
        return key.longValue();
    }

    @Override
    public void updateInput(Long executionId, String input) {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        if (jdbcTemplate == null || invalidExecutionId(executionId)) {
            return;
        }
        jdbcTemplate.update("UPDATE ai_agent_task_execution SET input = ? WHERE id = ?", input, executionId);
    }

    @Override
    public void markSucceeded(Long executionId, String output) {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        if (jdbcTemplate == null || invalidExecutionId(executionId)) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE ai_agent_task_execution
                SET status = 'SUCCESS', ended_at = ?, output = ?, error_message = NULL
                WHERE id = ?
                """, LocalDateTime.now(), output, executionId);
    }

    @Override
    public void markFailed(Long executionId, String errorMessage, String output) {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        if (jdbcTemplate == null || invalidExecutionId(executionId)) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE ai_agent_task_execution
                SET status = 'FAILED', ended_at = ?, output = ?, error_message = ?
                WHERE id = ?
                """, LocalDateTime.now(), output, errorMessage, executionId);
    }

    @Override
    public void markIncomplete(Long executionId, String status, String output) {
        if (!"WAITING_APPROVAL".equals(status) && !"CANCELED".equals(status)) {
            throw new IllegalArgumentException("SCHEDULED_INCOMPLETE_STATUS_INVALID");
        }
        JdbcTemplate jdbc = jdbcTemplate();
        if (jdbc == null || invalidExecutionId(executionId)) return;
        jdbc.update("""
                UPDATE ai_agent_task_execution SET status=?, ended_at=?, output=?, error_message=NULL
                WHERE id=? AND status='RUNNING'
                """, status, "CANCELED".equals(status) ? LocalDateTime.now() : null, output, executionId);
    }

    @Override
    public int reconcileWaitingRuns() {
        JdbcTemplate jdbc = jdbcTemplate();
        if (jdbc == null) return 0;
        // Read the authoritative Run; never execute or approve a waiting task here.
        return jdbc.update("""
                UPDATE ai_agent_task_execution t JOIN ai_ops_agent_run r
                  ON r.run_id=CONCAT('task_',t.schedule_id,'_',t.id)
                  AND r.project_id=JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_VALID(t.input),t.input,'{}'),'$.projectId'))
                SET t.status=CASE WHEN r.status='SUCCEEDED' THEN 'SUCCESS' ELSE r.status END,
                    t.ended_at=r.updated_at,
                    t.error_message=r.error_message,
                    t.output=COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.response_json,'$.content')),t.output)
                WHERE t.status='WAITING_APPROVAL' AND r.status IN ('SUCCEEDED','FAILED','CANCELED')
                """);
    }

    @Override
    public List<TaskExecutionView> list(Long scheduleId, int limit) {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        if (jdbcTemplate == null) {
            return List.of();
        }
        if (scheduleId == null) {
            return jdbcTemplate.query("""
                            SELECT id, schedule_id, task_name, agent_id, trigger_type, status, started_at, ended_at, input, output, error_message
                            FROM ai_agent_task_execution
                            ORDER BY started_at DESC
                            LIMIT ?
                            """,
                    (rs, rowNum) -> view(
                            rs.getLong("id"),
                            rs.getLong("schedule_id"),
                            rs.getString("task_name"),
                            rs.getString("agent_id"),
                            rs.getString("trigger_type"),
                            rs.getString("status"),
                            rs.getObject("started_at", LocalDateTime.class),
                            rs.getObject("ended_at", LocalDateTime.class),
                            rs.getString("input"),
                            rs.getString("output"),
                            rs.getString("error_message")),
                    limit);
        }
        return jdbcTemplate.query("""
                        SELECT id, schedule_id, task_name, agent_id, trigger_type, status, started_at, ended_at, input, output, error_message
                        FROM ai_agent_task_execution
                        WHERE schedule_id = ?
                        ORDER BY started_at DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> view(
                        rs.getLong("id"),
                        rs.getLong("schedule_id"),
                        rs.getString("task_name"),
                        rs.getString("agent_id"),
                        rs.getString("trigger_type"),
                        rs.getString("status"),
                        rs.getObject("started_at", LocalDateTime.class),
                        rs.getObject("ended_at", LocalDateTime.class),
                        rs.getString("input"),
                        rs.getString("output"),
                        rs.getString("error_message")),
                scheduleId, limit);
    }

    private TaskExecutionView view(
            Long id,
            Long scheduleId,
            String taskName,
            String agentId,
            String triggerType,
            String status,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            String input,
            String output,
            String errorMessage) {
        return new TaskExecutionView(
                id,
                scheduleId,
                taskName,
                agentId,
                triggerType,
                status,
                startedAt,
                endedAt,
                input,
                output,
                errorMessage);
    }

    private JdbcTemplate jdbcTemplate() {
        return jdbcTemplateProvider.getIfAvailable();
    }

    private String abbreviate(String value, int maxChars) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxChars ? trimmed : trimmed.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    private boolean invalidExecutionId(Long executionId) {
        return executionId == null || executionId <= 0;
    }
}
