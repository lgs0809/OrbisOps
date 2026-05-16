package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.alert.adapter.repository.IAlertRuleRepository;
import cn.lgs.orbisops.domain.alert.model.AlertRuleDefinition;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcAlertRuleRepository implements IAlertRuleRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, rule_name, status, source_type, alert_name_regex, severity_regex, service_regex,
                   match_labels_json, notification_channel_id, notification_target, webhook_secret, project_id,
                   agent_definition_id, agent_binding_mode, agent_version, agent_definition_hash,
                   question_template, range_minutes, prom_window, include_recent_logs, notify_channel,
                   sub_agent_max_iterations, node_timeout_seconds, max_evidence_items, dedup_window_seconds,
                   DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time,
                   DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS update_time
            FROM ai_ops_alert_trigger_rule
            """;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public JdbcAlertRuleRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public List<AlertRuleDefinition> list() {
        return jdbc().query(
                SELECT_COLUMNS + " ORDER BY update_time DESC, id DESC",
                rowMapper());
    }

    @Override
    public Optional<AlertRuleDefinition> find(Long id) {
        if (id == null || id <= 0) return Optional.empty();
        List<AlertRuleDefinition> rows = jdbc().query(
                SELECT_COLUMNS + " WHERE id=? LIMIT 1",
                rowMapper(), id);
        return rows.stream().findFirst();
    }

    @Override
    public AlertRuleDefinition save(AlertRuleDefinition rule) {
        if (rule == null) throw new IllegalArgumentException("ALERT_RULE_REQUIRED");
        Long id = rule.id();
        if (id == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc().update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO ai_ops_alert_trigger_rule
                        (rule_name, status, source_type, alert_name_regex, severity_regex, service_regex,
                         match_labels_json, notification_channel_id, notification_target, webhook_secret, project_id,
                         agent_definition_id, agent_binding_mode, agent_version, agent_definition_hash,
                         question_template, range_minutes, prom_window, include_recent_logs, notify_channel,
                         sub_agent_max_iterations, node_timeout_seconds, max_evidence_items, dedup_window_seconds)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                bind(statement, rule);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) throw new IllegalStateException("ALERT_RULE_GENERATED_ID_MISSING");
            id = key.longValue();
        } else {
            int updated = jdbc().update("""
                    UPDATE ai_ops_alert_trigger_rule
                    SET rule_name=?, status=?, source_type=?, alert_name_regex=?, severity_regex=?,
                        service_regex=?, match_labels_json=?, notification_channel_id=?, notification_target=?,
                        webhook_secret=?, project_id=?, agent_definition_id=?, agent_binding_mode=?,
                        agent_version=?, agent_definition_hash=?, question_template=?, range_minutes=?,
                        prom_window=?, include_recent_logs=?, notify_channel=?, sub_agent_max_iterations=?,
                        node_timeout_seconds=?, max_evidence_items=?, dedup_window_seconds=?
                    WHERE id=?
                    """,
                    rule.ruleName(), rule.status(), rule.sourceType(), rule.alertNameRegex(),
                    rule.severityRegex(), rule.serviceRegex(), JSON.toJSONString(rule.matchLabels()),
                    rule.notificationChannelId(), rule.notificationTarget(), rule.webhookSecret(), rule.projectId(),
                    rule.agentDefinitionId(), rule.agentBindingMode(), rule.agentVersion(), rule.agentDefinitionHash(),
                    rule.questionTemplate(), rule.rangeMinutes(), rule.promWindow(), boolInt(rule.includeRecentLogs()),
                    boolInt(rule.notifyChannel()), rule.subAgentMaxIterations(), rule.nodeTimeoutSeconds(),
                    rule.maxEvidenceItems(), rule.dedupWindowSeconds(), id);
            if (updated != 1) throw new IllegalStateException("ALERT_RULE_UPDATE_CONFLICT:" + id);
        }
        Long savedId = id;
        return find(savedId).orElseThrow(() ->
                new IllegalStateException("ALERT_RULE_READ_AFTER_SAVE_FAILED:" + savedId));
    }

    @Override
    public boolean updateStatus(Long id, int status) {
        return jdbc().update(
                "UPDATE ai_ops_alert_trigger_rule SET status=? WHERE id=?",
                status, id) == 1;
    }

    @Override
    public boolean delete(Long id) {
        return jdbc().update(
                "DELETE FROM ai_ops_alert_trigger_rule WHERE id=?",
                id) == 1;
    }

    private void bind(PreparedStatement statement, AlertRuleDefinition rule) throws java.sql.SQLException {
        statement.setString(1, rule.ruleName());
        statement.setInt(2, rule.status());
        statement.setString(3, rule.sourceType());
        statement.setString(4, rule.alertNameRegex());
        statement.setString(5, rule.severityRegex());
        statement.setString(6, rule.serviceRegex());
        statement.setString(7, JSON.toJSONString(rule.matchLabels()));
        statement.setString(8, rule.notificationChannelId());
        statement.setString(9, rule.notificationTarget());
        statement.setString(10, rule.webhookSecret());
        statement.setString(11, rule.projectId());
        statement.setString(12, rule.agentDefinitionId());
        statement.setString(13, rule.agentBindingMode());
        statement.setObject(14, rule.agentVersion());
        statement.setString(15, rule.agentDefinitionHash());
        statement.setString(16, rule.questionTemplate());
        statement.setInt(17, rule.rangeMinutes());
        statement.setString(18, rule.promWindow());
        statement.setInt(19, boolInt(rule.includeRecentLogs()));
        statement.setInt(20, boolInt(rule.notifyChannel()));
        statement.setInt(21, rule.subAgentMaxIterations());
        statement.setInt(22, rule.nodeTimeoutSeconds());
        statement.setInt(23, rule.maxEvidenceItems());
        statement.setInt(24, rule.dedupWindowSeconds());
    }

    private RowMapper<AlertRuleDefinition> rowMapper() {
        return (rs, rowNum) -> new AlertRuleDefinition(
                rs.getLong("id"),
                rs.getString("rule_name"),
                rs.getInt("status"),
                rs.getString("source_type"),
                rs.getString("alert_name_regex"),
                rs.getString("severity_regex"),
                rs.getString("service_regex"),
                parseLabels(rs.getString("match_labels_json")),
                rs.getString("notification_channel_id"),
                rs.getString("notification_target"),
                rs.getString("webhook_secret"),
                rs.getString("project_id"),
                rs.getString("agent_definition_id"),
                rs.getString("agent_binding_mode"),
                integer(rs.getObject("agent_version")),
                rs.getString("agent_definition_hash"),
                rs.getString("question_template"),
                rs.getInt("range_minutes"),
                rs.getString("prom_window"),
                rs.getInt("include_recent_logs") == 1,
                rs.getInt("notify_channel") == 1,
                rs.getInt("sub_agent_max_iterations"),
                rs.getInt("node_timeout_seconds"),
                rs.getInt("max_evidence_items"),
                rs.getInt("dedup_window_seconds"),
                rs.getString("create_time"),
                rs.getString("update_time"));
    }

    private Map<String, String> parseLabels(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, String> result = JSON.parseObject(
                    json, new TypeReference<LinkedHashMap<String, String>>() { });
            return result == null ? Map.of() : new LinkedHashMap<>(result);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private int boolInt(boolean value) {
        return value ? 1 : 0;
    }

    private JdbcTemplate jdbc() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        if (jdbc == null) throw new IllegalStateException("ALERT_RULE_STORE_UNAVAILABLE");
        return jdbc;
    }
}
