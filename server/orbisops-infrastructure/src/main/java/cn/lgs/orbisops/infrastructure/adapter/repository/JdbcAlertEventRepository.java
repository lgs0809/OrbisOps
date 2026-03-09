package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertEventRepository;
import cn.lgs.orbisops.domain.alert.model.AlertEventDraft;
import cn.lgs.orbisops.domain.alert.model.AlertEventSnapshot;
import cn.lgs.orbisops.domain.alert.model.AlertRunOutcome;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcAlertEventRepository implements IAlertEventRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, rule_id, rule_name, project_id, source_type, status, dedup_key, fingerprint,
                   alert_name, severity, service_name, receiver, run_id, run_status, final_summary,
                   DATE_FORMAT(completed_at, '%Y-%m-%d %H:%i:%s') AS completed_at,
                   error_message, labels_json, annotations_json, payload_json,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time
            FROM ai_ops_alert_trigger_event
            """;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public JdbcAlertEventRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public List<AlertEventSnapshot> list(int limit) {
        return jdbc().query(
                SELECT_COLUMNS + " ORDER BY id DESC LIMIT ?",
                rowMapper(), limit);
    }

    @Override
    public AlertEventSnapshot append(AlertEventDraft draft, boolean completed) {
        if (draft == null) throw new IllegalArgumentException("ALERT_EVENT_DRAFT_REQUIRED");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc().update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO ai_ops_alert_trigger_event
                    (rule_id, rule_name, project_id, source_type, status, dedup_key, fingerprint,
                     alert_name, severity, service_name, receiver, run_id, run_status, final_summary,
                     completed_at, error_message, labels_json, annotations_json, payload_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, draft.ruleId());
            statement.setString(2, draft.ruleName());
            statement.setString(3, draft.projectId());
            statement.setString(4, draft.sourceType());
            statement.setString(5, draft.status());
            statement.setString(6, draft.dispatchKey());
            statement.setString(7, draft.fingerprint());
            statement.setString(8, draft.alertName());
            statement.setString(9, draft.severity());
            statement.setString(10, draft.serviceName());
            statement.setString(11, draft.receiver());
            statement.setString(12, draft.runId());
            statement.setString(13, draft.runStatus());
            statement.setString(14, draft.finalSummary());
            statement.setObject(15, completed ? java.sql.Timestamp.from(Instant.now()) : null);
            statement.setString(16, draft.errorMessage());
            statement.setString(17, JSON.toJSONString(draft.labels()));
            statement.setString(18, JSON.toJSONString(draft.annotations()));
            statement.setString(19, JSON.toJSONString(draft.payload()));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("ALERT_EVENT_GENERATED_ID_MISSING");
        return find(key.longValue());
    }

    @Override
    public void updateRunOutcome(AlertRunOutcome outcome, boolean completed) {
        if (outcome == null) throw new IllegalArgumentException("ALERT_RUN_OUTCOME_REQUIRED");
        jdbc().update("""
                UPDATE ai_ops_alert_trigger_event
                SET run_status=?,
                    final_summary=COALESCE(NULLIF(?, ''), final_summary),
                    error_message=COALESCE(NULLIF(?, ''), error_message),
                    completed_at=CASE WHEN ? = 1 THEN CURRENT_TIMESTAMP ELSE completed_at END
                WHERE source_type=? AND fingerprint=? AND (?='' OR run_id=?)
                """,
                outcome.runStatus(),
                outcome.finalSummary(),
                outcome.errorMessage(),
                completed ? 1 : 0,
                outcome.sourceType(),
                outcome.triggerEventId(),
                outcome.runId(),
                outcome.runId());
    }

    private AlertEventSnapshot find(long id) {
        List<AlertEventSnapshot> rows = jdbc().query(
                SELECT_COLUMNS + " WHERE id=? LIMIT 1",
                rowMapper(), id);
        return rows.stream().findFirst().orElseThrow(() ->
                new IllegalStateException("ALERT_EVENT_READ_AFTER_APPEND_FAILED:" + id));
    }

    private RowMapper<AlertEventSnapshot> rowMapper() {
        return (rs, rowNum) -> new AlertEventSnapshot(
                rs.getLong("id"),
                longValue(rs.getObject("rule_id")),
                rs.getString("rule_name"),
                rs.getString("project_id"),
                rs.getString("source_type"),
                rs.getString("status"),
                rs.getString("dedup_key"),
                rs.getString("fingerprint"),
                rs.getString("alert_name"),
                rs.getString("severity"),
                rs.getString("service_name"),
                rs.getString("receiver"),
                rs.getString("run_id"),
                rs.getString("run_status"),
                rs.getString("final_summary"),
                rs.getString("completed_at"),
                rs.getString("error_message"),
                parseMap(rs.getString("labels_json")),
                parseMap(rs.getString("annotations_json")),
                parseMap(rs.getString("payload_json")),
                rs.getString("create_time"));
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> result = JSON.parseObject(
                    json, new TypeReference<LinkedHashMap<String, Object>>() { });
            return result == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(result));
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private JdbcTemplate jdbc() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) throw new IllegalStateException("ALERT_EVENT_STORE_UNAVAILABLE");
        return jdbc;
    }
}
