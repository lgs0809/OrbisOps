package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisRunRepository;
import cn.lgs.orbisops.domain.analysis.model.AnalysisRunSnapshot;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcAnalysisRunRepository implements IAnalysisRunRepository {

    private final JdbcTemplate jdbcTemplate;

    @Value("${orbisops.runs.jdbc-enabled:true}")
    private boolean jdbcEnabled = true;

    @Value("${orbisops.runs.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean tableInitialized;

    @Autowired
    public JdbcAnalysisRunRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public JdbcAnalysisRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean available() {
        return jdbcEnabled && jdbcTemplate != null;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void save(AnalysisRunSnapshot run) {
        JdbcTemplate template = requiredTemplate();
        ensureTable(template);
        template.update("""
                        INSERT INTO ai_ops_analysis_task
                        (run_id, project_id, trigger_source, status, request_json, response_json,
                         error_message, created_at, updated_at, duration_ms)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          status=VALUES(status), request_json=VALUES(request_json), response_json=VALUES(response_json),
                          error_message=VALUES(error_message), updated_at=VALUES(updated_at), duration_ms=VALUES(duration_ms)
                        """,
                run.runId(), run.projectId(), run.triggerSource(), run.status(), run.requestJson(), run.responseJson(),
                run.errorMessage(), run.createdAt(), run.updatedAt(), run.durationMs());
    }

    @Override
    public Optional<AnalysisRunSnapshot> find(String runId) {
        JdbcTemplate template = requiredTemplate();
        ensureTable(template);
        return template.queryForList("""
                        SELECT run_id, project_id, trigger_source, status, request_json, response_json,
                               error_message, created_at, updated_at, duration_ms
                        FROM ai_ops_analysis_task
                        WHERE run_id = ?
                        LIMIT 1
                        """, runId)
                .stream()
                .findFirst()
                .map(this::snapshot);
    }

    @Override
    public List<AnalysisRunSnapshot> findRecent(int limit) {
        JdbcTemplate template = requiredTemplate();
        ensureTable(template);
        return template.queryForList("""
                        SELECT run_id, project_id, trigger_source, status, request_json, response_json,
                               error_message, created_at, updated_at, duration_ms
                        FROM ai_ops_analysis_task
                        ORDER BY id DESC
                        LIMIT ?
                        """, limit)
                .stream()
                .map(this::snapshot)
                .toList();
    }

    @Override
    public int countActiveByProject(String projectId) {
        JdbcTemplate template = requiredTemplate();
        ensureTable(template);
        Integer count = template.queryForObject("""
                        SELECT COUNT(1) FROM ai_ops_analysis_task
                        WHERE project_id=? AND status IN ('PENDING','RUNNING')
                        """, Integer.class, projectId);
        return count == null ? 0 : count;
    }

    private AnalysisRunSnapshot snapshot(Map<String, Object> row) {
        return new AnalysisRunSnapshot(
                text(row.get("run_id")),
                text(row.get("project_id")),
                text(row.get("trigger_source")),
                text(row.get("status")),
                nullableText(row.get("request_json")),
                nullableText(row.get("response_json")),
                nullableText(row.get("error_message")),
                text(row.get("created_at")),
                text(row.get("updated_at")),
                longObject(row.get("duration_ms")));
    }

    private JdbcTemplate requiredTemplate() {
        if (!available()) throw new IllegalStateException("ANALYSIS_TASK_STORE_UNAVAILABLE：异步分析任务存储未配置");
        return jdbcTemplate;
    }

    private void ensureTable(JdbcTemplate template) {
        if (tableInitialized) return;
        synchronized (this) {
            if (tableInitialized) return;
            if (autoInit) {
                template.execute("""
                        CREATE TABLE IF NOT EXISTS ai_ops_analysis_task (
                          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                          run_id VARCHAR(80) NOT NULL COMMENT 'Agent运行ID',
                          project_id VARCHAR(80) NOT NULL COMMENT '项目ID',
                          trigger_source VARCHAR(48) NOT NULL DEFAULT '' COMMENT '触发来源',
                          status VARCHAR(32) NOT NULL COMMENT '任务状态',
                          request_json MEDIUMTEXT NULL COMMENT '请求JSON',
                          response_json MEDIUMTEXT NULL COMMENT '响应JSON',
                          error_message TEXT NULL COMMENT '错误信息',
                          created_at VARCHAR(32) NOT NULL COMMENT '创建时间',
                          updated_at VARCHAR(32) NOT NULL COMMENT '更新时间',
                          duration_ms BIGINT NULL COMMENT '耗时毫秒',
                          create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_run_id (run_id),
                          KEY idx_project_status (project_id, status),
                          KEY idx_trigger_status (trigger_source, status),
                          KEY idx_create_time (create_time)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维分析异步任务登记表'
                        """);
            }
            tableInitialized = true;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullableText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longObject(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
