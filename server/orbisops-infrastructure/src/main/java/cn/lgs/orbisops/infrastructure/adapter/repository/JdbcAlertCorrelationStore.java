package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.incident.AlertCorrelationStore;
import cn.lgs.orbisops.domain.incident.correlation.AlertCorrelationPolicy;
import cn.lgs.orbisops.domain.incident.correlation.AlertCorrelationPolicy.Decision;
import cn.lgs.orbisops.domain.incident.correlation.CorrelationSignal;
import cn.lgs.orbisops.domain.incident.correlation.CorrelationTopologyEdge;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Repository
public class JdbcAlertCorrelationStore implements AlertCorrelationStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public JdbcAlertCorrelationStore(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbc,
            @Qualifier("mysqlTransactionManager") ObjectProvider<PlatformTransactionManager> manager) {
        this.jdbc = jdbc.getIfAvailable();
        this.tx = manager.getIfAvailable() == null ? null : new TransactionTemplate(manager.getObject());
    }
    @Override public <T> T inScope(String project, String environment, Supplier<T> action) {
        if (jdbc == null || tx == null) throw new IllegalStateException("CORRELATION_STORE_UNAVAILABLE");
        return tx.execute(status -> {
            // One short database transaction per scope; no network/model call under this lock.
            jdbc.update("INSERT INTO ai_ops_alert_correlation_scope (scope_key, project_id, environment) VALUES (?,?,?) "
                    + "ON DUPLICATE KEY UPDATE scope_key=VALUES(scope_key)", scope(project, environment), project, environment);
            return action.get();
        });
    }
    @Override public boolean recorded(long eventId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_alert_correlation_decision WHERE event_id=?", Long.class, eventId) > 0;
    }
    @Override public List<Group> candidates(CorrelationSignal signal) {
        if (signal.startedAt() == null || signal.environment().isBlank()) return List.of();
        long epoch = signal.startedAt().getEpochSecond();
        return jdbc.query("SELECT group_id, anchor_json FROM ai_ops_alert_correlation_group "
                        + "WHERE project_id=? AND environment=? AND merged_into IS NULL AND anchor_epoch BETWEEN ? AND ? "
                        + "ORDER BY anchor_epoch, group_id LIMIT ?",
                (rs, n) -> new Group(rs.getString("group_id"), AlertCorrelationJson.signal(rs.getString("anchor_json")),
                        members(rs.getString("group_id"))), signal.projectId(), signal.environment(),
                epoch - AlertCorrelationPolicy.WINDOW_SECONDS, epoch + AlertCorrelationPolicy.WINDOW_SECONDS,
                AlertCorrelationPolicy.MAX_CANDIDATES);
    }
    private List<CorrelationSignal> members(String group) {
        return jdbc.query("SELECT signal_json FROM ai_ops_alert_correlation_member WHERE group_id=? ORDER BY incident_id",
                (rs, n) -> AlertCorrelationJson.signal(rs.getString(1)), group);
    }
    @Override public List<CorrelationTopologyEdge> topology(String project, String environment) {
        return jdbc.query("SELECT edges_json FROM ai_ops_alert_correlation_topology WHERE scope_key=?",
                (rs, n) -> AlertCorrelationJson.edges(rs.getString(1)), scope(project, environment)).stream().findFirst().orElse(List.of());
    }
    @Override public void saveTopology(String project, String environment, List<CorrelationTopologyEdge> edges, String actor) {
        String json = AlertCorrelationJson.edges(edges);
        if (topology(project, environment).equals(edges)) return;
        jdbc.update("INSERT INTO ai_ops_alert_correlation_topology (scope_key,edges_json,updated_by) VALUES (?,?,?) "
                + "ON DUPLICATE KEY UPDATE edges_json=VALUES(edges_json),updated_by=VALUES(updated_by),revision=revision+1",
                scope(project, environment), json, actor);
        revision(scope(project, environment), "TOPOLOGY_CONFIGURED", Map.of("projectId", project, "environment", environment,
                "actor", actor, "edges", JSON.parseArray(json)));
    }
    @Override public void record(String group, CorrelationSignal signal, Decision decision) {
        String signalJson = AlertCorrelationJson.signal(signal);
        String decisionJson = JSON.toJSONString(Map.of("join", decision.join(), "score", decision.score(),
                "reason", decision.reason(), "evidenceRefs", decision.evidenceRefs(), "policyVersion", decision.policyVersion()));
        jdbc.update("INSERT INTO ai_ops_alert_correlation_group (group_id,project_id,environment,anchor_json,anchor_epoch) VALUES (?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE revision=revision+1", group, signal.projectId(), signal.environment(), signalJson,
                signal.startedAt() == null ? null : signal.startedAt().getEpochSecond());
        jdbc.update("INSERT INTO ai_ops_alert_correlation_member (group_id,incident_id,signal_json,decision_json,last_event_id) VALUES (?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE signal_json=IF(last_event_id<VALUES(last_event_id),VALUES(signal_json),signal_json),"
                        + "last_event_id=GREATEST(last_event_id,VALUES(last_event_id)),occurrence_count=occurrence_count+1",
                group, signal.incidentId(), signalJson, decisionJson, signal.eventId());
        jdbc.update("INSERT INTO ai_ops_alert_correlation_decision (event_id,group_id,signal_json,decision_json) VALUES (?,?,?,?)",
                signal.eventId(), group, signalJson, decisionJson);
    }
    @Override public void merge(String source, String target, Map<String, Decision> decisions) {
        for (Map.Entry<String, Decision> entry : decisions.entrySet()) {
            Decision d = entry.getValue();
            String decision = JSON.toJSONString(Map.of("join", true, "score", d.score(), "reason", d.reason(),
                    "evidenceRefs", d.evidenceRefs(), "policyVersion", d.policyVersion()));
            jdbc.update("INSERT INTO ai_ops_alert_correlation_member (group_id,incident_id,signal_json,decision_json,last_event_id,occurrence_count) "
                    + "SELECT ?,m.incident_id,m.signal_json,?,m.last_event_id,m.occurrence_count FROM ai_ops_alert_correlation_member m WHERE m.group_id=? AND m.incident_id=? "
                    + "ON DUPLICATE KEY UPDATE occurrence_count=ai_ops_alert_correlation_member.occurrence_count+VALUES(occurrence_count)", target, decision, source, entry.getKey());
        }
        jdbc.update("UPDATE ai_ops_alert_correlation_group SET merged_into=?,revision=revision+1 WHERE group_id=? AND merged_into IS NULL", target, source);
        jdbc.update("UPDATE ai_ops_alert_correlation_group SET revision=revision+1 WHERE group_id=?", target);
        revision(target, "LATE_EVIDENCE_REGROUP", Map.of("fromGroupId", source, "members", decisions.keySet(),
                "policyVersion", AlertCorrelationPolicy.VERSION));
    }
    @Override public List<Map<String, Object>> groups(String project, String environment, int limit) {
        return jdbc.query("SELECT group_id,environment,anchor_json,revision FROM ai_ops_alert_correlation_group "
                        + "WHERE project_id=? AND (?='' OR environment=?) AND merged_into IS NULL ORDER BY updated_at DESC,group_id LIMIT ?", (rs,n) -> {
                    String group = rs.getString("group_id");
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("groupId", group); view.put("environment", rs.getString("environment"));
                    view.put("revision", rs.getLong("revision")); view.put("rootCauseConfirmed", false);
                    view.put("anchor", JSON.parseObject(rs.getString("anchor_json")));
                    view.put("members", jdbc.query("SELECT signal_json,decision_json,occurrence_count FROM ai_ops_alert_correlation_member WHERE group_id=? ORDER BY last_event_id",
                            (row,index) -> Map.of("signal", JSON.parseObject(row.getString(1)), "decision", JSON.parseObject(row.getString(2)),
                                    "occurrenceCount", row.getLong(3)), group));
                    return view;
                }, project, environment, environment, limit);
    }
    @Override public List<CorrelationSignal> pending(int limit) { return new JdbcAlertCorrelationSignalReader(jdbc).pending(limit); }
    private void revision(String group, String action, Map<String,Object> detail) {
        jdbc.update("INSERT INTO ai_ops_alert_correlation_revision (group_id,action_type,detail_json) VALUES (?,?,?)", group, action, JSON.toJSONString(detail));
    }
    static String scope(String project, String environment) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((project + "\u0000" + environment).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
