package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.incident.correlation.CorrelationSignal;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Replays the durable authenticated alert ledger; a failed correlation write cannot lose a signal. */
final class JdbcAlertCorrelationSignalReader {
    private final JdbcTemplate jdbc;
    JdbcAlertCorrelationSignalReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    List<CorrelationSignal> pending(int limit) {
        return jdbc.query("""
                SELECT e.id,e.project_id,e.status,e.service_name,e.alert_name,e.labels_json,e.payload_json,
                       UNIX_TIMESTAMP(e.create_time) received_epoch,i.incident_id
                FROM ai_ops_alert_trigger_event e
                JOIN ai_ops_incident i ON i.dedup_key=CONCAT(e.project_id,':',e.dedup_key)
                LEFT JOIN ai_ops_alert_correlation_decision d ON d.event_id=e.id
                WHERE d.event_id IS NULL
                  AND (e.status IN ('TRIGGERED','QUEUED','FAILED','DEDUPED') OR e.status LIKE 'RECOVERY_%')
                ORDER BY e.id LIMIT ?
                """, (rs, n) -> {
            JSONObject labels = object(rs.getString("labels_json")), payload = object(rs.getString("payload_json"));
            Map<String, String> identities = new LinkedHashMap<>();
            for (String key : List.of("correlation_id", "trace_id", "resource_id", "change_id", "instance", "pod")) {
                String value = labels.getString(key);
                if (value != null && !value.isBlank() && value.length() <= 240) identities.put(key, value.trim());
            }
            String environment = labels.getString("environment");
            if (environment == null) environment = labels.getString("env");
            if (environment != null && environment.length() > 80) environment = "";
            String entity = labels.getString("entity_id");
            if (entity == null || entity.isBlank()) entity = rs.getString("service_name");
            return new CorrelationSignal(rs.getLong("id"), rs.getString("incident_id"), rs.getString("project_id"), environment,
                    entity, AlertCorrelationJson.instant(payload.getString("startsAt")), Instant.ofEpochSecond(rs.getLong("received_epoch")),
                    identities, rs.getString("alert_name"), rs.getString("status").startsWith("RECOVERY_"));
        }, limit);
    }
    private JSONObject object(String json) { return json == null || json.isBlank() ? new JSONObject() : JSON.parseObject(json); }
}
