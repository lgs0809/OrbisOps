package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertOutboxRepository;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateEventType;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxDraft;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxEntry;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxFailurePlan;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxStatus;
import cn.lgs.orbisops.domain.alert.model.AlertRunRequest;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcAlertOutboxRepository implements IAlertOutboxRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, dedup_key, rule_id, project_id, fingerprint, aggregate_key, event_type,
                   status, request_json, retry_count, priority
            FROM ai_ops_alert_trigger_outbox
            """;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public JdbcAlertOutboxRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public long countActiveByProject(String projectId) {
        Number value = jdbc().queryForObject("""
                SELECT COUNT(1)
                FROM ai_ops_alert_trigger_outbox
                WHERE project_id=? AND status IN ('PENDING','RUNNING','FAILED')
                """, Number.class, projectId);
        return value == null ? 0L : value.longValue();
    }

    @Override
    public boolean preemptOneLowerPriority(String projectId, int priority) {
        return jdbc().update("""
                UPDATE ai_ops_alert_trigger_outbox
                SET status='DEAD_LETTER', dead_letter_at=CURRENT_TIMESTAMP,
                    locked_token=NULL, locked_at=NULL,
                    error_message='PREEMPTED_BY_CRITICAL_ALERT'
                WHERE project_id=? AND status IN ('PENDING','FAILED') AND priority < ?
                ORDER BY priority ASC, id ASC
                LIMIT 1
                """, projectId, priority) == 1;
    }

    @Override
    public void upsert(AlertOutboxDraft draft) {
        jdbc().update("""
                INSERT INTO ai_ops_alert_trigger_outbox
                (dedup_key, rule_id, project_id, fingerprint, aggregate_key, event_type, priority,
                 status, request_json, payload_json, next_retry_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                  request_json=VALUES(request_json), payload_json=VALUES(payload_json),
                  project_id=VALUES(project_id), aggregate_key=VALUES(aggregate_key),
                  event_type=VALUES(event_type), priority=VALUES(priority)
                """,
                draft.dispatchKey(),
                draft.ruleId(),
                draft.projectId(),
                draft.fingerprint(),
                draft.aggregateKey(),
                draft.eventType().name(),
                draft.priority(),
                encodeRequest(draft.request()),
                JSON.toJSONString(draft.payload()));
    }

    @Override
    public Optional<AlertOutboxEntry> findByDispatchKey(String dispatchKey) {
        List<AlertOutboxEntry> rows = jdbc().query(
                SELECT_COLUMNS + " WHERE dedup_key=? LIMIT 1",
                rowMapper(), dispatchKey);
        return rows.stream().findFirst();
    }

    @Override
    public List<AlertOutboxEntry> listDispatchable(int maxAttempts, int limit) {
        return jdbc().query(SELECT_COLUMNS + """
                WHERE status IN ('PENDING','FAILED')
                  AND retry_count < ?
                  AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP)
                ORDER BY priority DESC, id ASC
                LIMIT ?
                """, rowMapper(), maxAttempts, limit);
    }

    @Override
    public boolean claim(long id, String claimId, int maxAttempts) {
        return jdbc().update("""
                UPDATE ai_ops_alert_trigger_outbox
                SET status='RUNNING', locked_token=?, locked_at=CURRENT_TIMESTAMP
                WHERE id=? AND status IN ('PENDING','FAILED') AND retry_count < ?
                """, claimId, id, maxAttempts) == 1;
    }

    @Override
    public boolean markSucceeded(long id, String claimId, String runId) {
        return jdbc().update("""
                UPDATE ai_ops_alert_trigger_outbox
                SET status='SUCCEEDED', run_id=?, error_message=NULL,
                    locked_token=NULL, locked_at=NULL
                WHERE id=? AND locked_token=? AND status='RUNNING'
                """, runId, id, claimId) == 1;
    }

    @Override
    public boolean markFailed(long id, String claimId, AlertOutboxFailurePlan failure) {
        if (failure.deadLetter()) {
            return jdbc().update("""
                    UPDATE ai_ops_alert_trigger_outbox
                    SET status='DEAD_LETTER', retry_count=?, next_retry_at=NULL,
                        locked_token=NULL, locked_at=NULL,
                        dead_letter_at=COALESCE(dead_letter_at, CURRENT_TIMESTAMP),
                        error_message=?
                    WHERE id=? AND locked_token=? AND status='RUNNING'
                    """,
                    failure.nextRetryCount(),
                    failure.errorMessage(),
                    id,
                    claimId) == 1;
        }
        return jdbc().update("""
                UPDATE ai_ops_alert_trigger_outbox
                SET status='FAILED', retry_count=?,
                    next_retry_at=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND),
                    locked_token=NULL, locked_at=NULL,
                    error_message=?
                WHERE id=? AND locked_token=? AND status='RUNNING'
                """,
                failure.nextRetryCount(),
                failure.retryDelaySeconds(),
                failure.errorMessage(),
                id,
                claimId) == 1;
    }

    @Override
    public int recoverStale(int lockTimeoutSeconds, int maxAttempts) {
        return jdbc().update("""
                UPDATE ai_ops_alert_trigger_outbox
                SET status='FAILED', locked_token=NULL, locked_at=NULL,
                    next_retry_at=CURRENT_TIMESTAMP,
                    error_message='OUTBOX_STALE_LOCK_RECOVERED'
                WHERE status='RUNNING'
                  AND locked_at IS NOT NULL
                  AND locked_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? SECOND)
                  AND retry_count < ?
                """, lockTimeoutSeconds, maxAttempts);
    }

    @Override
    public int deadLetterExhausted(int maxAttempts) {
        return jdbc().update("""
                UPDATE ai_ops_alert_trigger_outbox
                SET status='DEAD_LETTER', dead_letter_at=COALESCE(dead_letter_at, CURRENT_TIMESTAMP),
                    locked_token=NULL, locked_at=NULL,
                    error_message=COALESCE(NULLIF(error_message, ''), 'OUTBOX_MAX_ATTEMPTS_EXCEEDED')
                WHERE status IN ('PENDING','FAILED') AND retry_count >= ?
                """, maxAttempts);
    }

    private RowMapper<AlertOutboxEntry> rowMapper() {
        return (rs, rowNum) -> new AlertOutboxEntry(
                rs.getLong("id"),
                rs.getString("dedup_key"),
                rs.getLong("rule_id"),
                rs.getString("project_id"),
                rs.getString("fingerprint"),
                rs.getString("aggregate_key"),
                AlertAggregateEventType.valueOf(rs.getString("event_type")),
                AlertOutboxStatus.valueOf(rs.getString("status")),
                decodeRequest(rs.getString("request_json")),
                rs.getInt("retry_count"),
                rs.getInt("priority"));
    }

    private String encodeRequest(AlertRunRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", request.runId());
        data.put("requestedBy", request.requestedBy());
        data.put("projectId", request.projectId());
        data.put("agentDefinitionId", request.agentDefinitionId());
        data.put("agentVersion", request.agentVersion());
        data.put("agentDefinitionSnapshotJson", request.agentDefinitionSnapshotJson());
        data.put("query", request.query());
        data.put("question", request.question());
        data.put("rangeMinutes", request.rangeMinutes());
        data.put("promWindow", request.promWindow());
        data.put("includeRecentLogs", request.includeRecentLogs());
        data.put("maxRounds", request.maxRounds());
        data.put("subAgentMaxIterations", request.subAgentMaxIterations());
        data.put("nodeTimeoutSeconds", request.nodeTimeoutSeconds());
        data.put("maxEvidenceItems", request.maxEvidenceItems());
        data.put("notifyChannel", request.notifyChannel());
        data.put("notificationChannelId", request.notificationChannelId());
        data.put("notificationTarget", request.notificationTarget());
        data.put("executionStyle", request.executionStyle());
        data.put("triggerSource", request.triggerSource());
        data.put("triggerEventId", request.triggerEventId());
        return JSON.toJSONString(data);
    }

    private AlertRunRequest decodeRequest(String json) {
        JSONObject data = JSON.parseObject(json);
        if (data == null) throw new IllegalStateException("ALERT_OUTBOX_REQUEST_JSON_INVALID");
        return new AlertRunRequest(
                data.getString("runId"),
                data.getString("requestedBy"),
                data.getString("projectId"),
                data.getString("agentDefinitionId"),
                data.getInteger("agentVersion"),
                data.getString("agentDefinitionSnapshotJson"),
                data.getString("query"),
                data.getString("question"),
                data.getInteger("rangeMinutes"),
                data.getString("promWindow"),
                data.getBoolean("includeRecentLogs"),
                data.getInteger("maxRounds"),
                data.getInteger("subAgentMaxIterations"),
                data.getInteger("nodeTimeoutSeconds"),
                data.getInteger("maxEvidenceItems"),
                data.getBoolean("notifyChannel"),
                data.getString("notificationChannelId"),
                data.getString("notificationTarget"),
                data.getString("executionStyle"),
                data.getString("triggerSource"),
                data.getString("triggerEventId"));
    }

    private JdbcTemplate jdbc() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) throw new IllegalStateException("ALERT_OUTBOX_STORE_UNAVAILABLE");
        return jdbc;
    }
}
