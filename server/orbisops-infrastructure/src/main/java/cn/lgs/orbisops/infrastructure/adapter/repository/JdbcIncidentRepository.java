package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.incident.adapter.repository.IIncidentRepository;
import cn.lgs.orbisops.domain.incident.model.IncidentAlertDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentProductMetricsProjection;
import cn.lgs.orbisops.domain.incident.model.IncidentRelation;
import cn.lgs.orbisops.domain.incident.model.IncidentRunSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineEntry;
import cn.lgs.orbisops.domain.incident.model.IncidentWatcher;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcIncidentRepository implements IIncidentRepository {

    private static final String INCIDENT_SELECT = """
            SELECT *, DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time_text,
                      DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS update_time_text,
                      DATE_FORMAT(first_seen_at, '%Y-%m-%d %H:%i:%s') AS first_seen_at_text,
                      DATE_FORMAT(last_seen_at, '%Y-%m-%d %H:%i:%s') AS last_seen_at_text,
                      DATE_FORMAT(acknowledged_at, '%Y-%m-%d %H:%i:%s') AS acknowledged_at_text,
                      DATE_FORMAT(resolved_at, '%Y-%m-%d %H:%i:%s') AS resolved_at_text,
                      DATE_FORMAT(reviewed_at, '%Y-%m-%d %H:%i:%s') AS reviewed_at_text
            FROM ai_ops_incident
            """;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public JdbcIncidentRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public List<IncidentSnapshot> list(String projectId, IncidentStatus status, int limit) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) return List.of();
        boolean hasProject = projectId != null && !projectId.isBlank();
        if (hasProject && status != null) {
            return jdbc.query(
                    INCIDENT_SELECT + " WHERE project_id=? AND status=? ORDER BY update_time DESC, id DESC LIMIT ?",
                    incidentMapper(), projectId.trim(), status.name(), limit);
        }
        if (hasProject) {
            return jdbc.query(
                    INCIDENT_SELECT + " WHERE project_id=? ORDER BY update_time DESC, id DESC LIMIT ?",
                    incidentMapper(), projectId.trim(), limit);
        }
        if (status != null) {
            return jdbc.query(
                    INCIDENT_SELECT + " WHERE status=? ORDER BY update_time DESC, id DESC LIMIT ?",
                    incidentMapper(), status.name(), limit);
        }
        return jdbc.query(
                INCIDENT_SELECT + " ORDER BY update_time DESC, id DESC LIMIT ?",
                incidentMapper(), limit);
    }

    @Override
    public Optional<IncidentSnapshot> find(String incidentId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null || incidentId == null || incidentId.isBlank()) return Optional.empty();
        List<IncidentSnapshot> rows = jdbc.query(
                INCIDENT_SELECT + " WHERE incident_id=? LIMIT 1",
                incidentMapper(), incidentId.trim());
        return rows.stream().findFirst();
    }

    @Override
    public IncidentSnapshot create(IncidentDraft draft) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) return fallback(draft);
        jdbc.update("""
                        INSERT INTO ai_ops_incident
                        (incident_id, project_id, title, status, severity, service_name, source_type, summary,
                         labels_json, metadata_json, affected_resources_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                draft.incidentId(),
                draft.projectId(),
                draft.title(),
                draft.status().name(),
                draft.severity(),
                draft.serviceName(),
                draft.sourceType(),
                draft.summary(),
                JSON.toJSONString(draft.labels()),
                JSON.toJSONString(draft.metadata()),
                JSON.toJSONString(draft.affectedResources()));
        return find(draft.incidentId()).orElseGet(() -> fallback(draft));
    }

    @Override
    public Optional<IncidentSnapshot> upsertAlert(IncidentAlertDraft draft) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) return Optional.empty();
        jdbc.update("""
                        INSERT INTO ai_ops_incident
                        (incident_id, project_id, title, status, severity, service_name, source_type, fingerprint,
                         dedup_key, current_run_id, summary, labels_json, metadata_json, affected_resources_json)
                        VALUES (?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          current_run_id = COALESCE(VALUES(current_run_id), current_run_id),
                          summary = COALESCE(VALUES(summary), summary),
                          resolved_at = IF(status IN ('RESOLVED','CLOSED','REVIEWED'), NULL, resolved_at),
                          reviewed_at = IF(status IN ('RESOLVED','CLOSED','REVIEWED'), NULL, reviewed_at),
                          status = IF(status IN ('RESOLVED','CLOSED','REVIEWED'), 'OPEN', status),
                          occurrence_count = occurrence_count + 1,
                          last_seen_at = CURRENT_TIMESTAMP,
                          affected_resources_json = VALUES(affected_resources_json),
                          update_time = CURRENT_TIMESTAMP
                        """,
                draft.incidentId(),
                draft.projectId(),
                draft.title(),
                draft.severity(),
                draft.serviceName(),
                draft.sourceType(),
                draft.fingerprint(),
                draft.projectDedupKey(),
                blankToNull(draft.runId()),
                blankToNull(draft.summary()),
                draft.labelsJson(),
                JSON.toJSONString(draft.metadata()),
                JSON.toJSONString(draft.affectedResources()));
        return find(draft.incidentId());
    }

    @Override
    public IncidentSnapshot updateStatus(String incidentId, IncidentStatus status) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) throw new IllegalStateException("INCIDENT_STORE_UNAVAILABLE");
        jdbc.update("""
                        UPDATE ai_ops_incident
                        SET status = ?,
                            acknowledged_at = CASE
                              WHEN ? IN ('INVESTIGATING','ACTION_REQUIRED','REMEDIATING','VERIFYING') AND acknowledged_at IS NULL
                              THEN CURRENT_TIMESTAMP ELSE acknowledged_at END,
                            resolved_at = CASE
                              WHEN ? = 'RESOLVED' THEN CURRENT_TIMESTAMP
                              WHEN ? NOT IN ('RESOLVED','CLOSED') THEN NULL
                              ELSE resolved_at END,
                            reviewed_at = CASE WHEN ? = 'CLOSED' THEN CURRENT_TIMESTAMP ELSE reviewed_at END,
                            update_time = CURRENT_TIMESTAMP
                        WHERE incident_id = ?
                        """,
                status.name(), status.name(), status.name(), status.name(), status.name(), incidentId);
        return find(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + incidentId));
    }

    @Override
    public IncidentSnapshot assignOwner(String incidentId, String ownerUserId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) throw new IllegalStateException("INCIDENT_STORE_UNAVAILABLE");
        jdbc.update("""
                        UPDATE ai_ops_incident
                        SET owner_user_id=?, update_time=CURRENT_TIMESTAMP
                        WHERE incident_id=?
                        """, blankToNull(ownerUserId), incidentId);
        return find(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + incidentId));
    }

    @Override
    public Optional<IncidentTimelineEntry> appendTimeline(IncidentTimelineDraft draft) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) return Optional.empty();
        jdbc.update("""
                        INSERT INTO ai_ops_incident_timeline
                        (incident_id, event_type, title, detail, actor, ref_type, ref_id, payload_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                draft.incidentId(),
                draft.eventType(),
                draft.title(),
                blankToNull(draft.detail()),
                draft.actor(),
                blankToNull(draft.refType()),
                blankToNull(draft.refId()),
                draft.payload().isEmpty() ? null : JSON.toJSONString(draft.payload()));
        return timeline(draft.incidentId(), 1).stream().findFirst();
    }

    @Override
    public void linkRun(String incidentId, String runId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) return;
        jdbc.update("""
                        INSERT INTO ai_ops_incident_run (incident_id, run_id)
                        VALUES (?, ?)
                        ON DUPLICATE KEY UPDATE create_time = create_time
                        """, incidentId, runId);
        jdbc.update(
                "UPDATE ai_ops_incident SET current_run_id=? WHERE incident_id=?",
                runId, incidentId);
    }

    @Override
    public List<IncidentTimelineEntry> timeline(String incidentId, int limit) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) return List.of();
        return jdbc.query("""
                SELECT id, incident_id, event_type, title, detail, actor, ref_type, ref_id, payload_json,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time
                FROM ai_ops_incident_timeline
                WHERE incident_id = ?
                ORDER BY id DESC
                LIMIT ?
                """, timelineMapper(), incidentId, limit);
    }

    @Override
    public List<IncidentRunSnapshot> runs(String incidentId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) return List.of();
        return jdbc.query("""
                SELECT r.run_id, r.status, r.error_message, r.created_at, r.updated_at, r.duration_ms
                FROM ai_ops_incident_run ir
                LEFT JOIN ai_ops_agent_run r ON r.run_id = ir.run_id
                WHERE ir.incident_id = ?
                ORDER BY ir.id DESC
                """, (rs, rowNum) -> new IncidentRunSnapshot(
                rs.getString("run_id"),
                rs.getString("status"),
                rs.getString("error_message"),
                rs.getString("created_at"),
                rs.getString("updated_at"),
                nullableLong(rs, "duration_ms")), incidentId);
    }

    @Override
    public List<IncidentWatcher> watchers(String incidentId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) return List.of();
        return jdbc.query("""
                SELECT incident_id, user_id, created_by,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time
                FROM ai_ops_incident_watcher
                WHERE incident_id=?
                ORDER BY id ASC
                """, (rs, rowNum) -> new IncidentWatcher(
                rs.getString("incident_id"),
                rs.getString("user_id"),
                rs.getString("created_by"),
                rs.getString("create_time")), incidentId);
    }

    @Override
    public void addWatcher(String incidentId, String userId, String createdBy) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) throw new IllegalStateException("INCIDENT_STORE_UNAVAILABLE");
        jdbc.update("""
                INSERT INTO ai_ops_incident_watcher (incident_id, user_id, created_by)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE created_by=VALUES(created_by)
                """, incidentId, userId, blankToNull(createdBy));
    }

    @Override
    public void removeWatcher(String incidentId, String userId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) throw new IllegalStateException("INCIDENT_STORE_UNAVAILABLE");
        jdbc.update("DELETE FROM ai_ops_incident_watcher WHERE incident_id=? AND user_id=?", incidentId, userId);
    }

    @Override
    public List<IncidentRelation> relations(String incidentId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) return List.of();
        return jdbc.query("""
                SELECT incident_id_low, incident_id_high, relation_type, created_by,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time
                FROM ai_ops_incident_relation
                WHERE incident_id_low=? OR incident_id_high=?
                ORDER BY id DESC
                """, (rs, rowNum) -> {
            String low = rs.getString("incident_id_low");
            String high = rs.getString("incident_id_high");
            String related = incidentId.equals(low) ? high : low;
            return new IncidentRelation(
                    incidentId,
                    related,
                    rs.getString("relation_type"),
                    rs.getString("created_by"),
                    rs.getString("create_time"));
        }, incidentId, incidentId);
    }

    @Override
    public void addRelation(String incidentId, String relatedIncidentId, String relationType, String createdBy) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) throw new IllegalStateException("INCIDENT_STORE_UNAVAILABLE");
        String low = incidentId.compareTo(relatedIncidentId) <= 0 ? incidentId : relatedIncidentId;
        String high = incidentId.compareTo(relatedIncidentId) <= 0 ? relatedIncidentId : incidentId;
        jdbc.update("""
                INSERT INTO ai_ops_incident_relation
                (incident_id_low, incident_id_high, relation_type, created_by)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE created_by=VALUES(created_by)
                """, low, high, relationType, blankToNull(createdBy));
    }

    @Override
    public void removeRelation(String incidentId, String relatedIncidentId, String relationType) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) throw new IllegalStateException("INCIDENT_STORE_UNAVAILABLE");
        String low = incidentId.compareTo(relatedIncidentId) <= 0 ? incidentId : relatedIncidentId;
        String high = incidentId.compareTo(relatedIncidentId) <= 0 ? relatedIncidentId : incidentId;
        jdbc.update("""
                DELETE FROM ai_ops_incident_relation
                WHERE incident_id_low=? AND incident_id_high=? AND relation_type=?
                """, low, high, relationType);
    }

    @Override
    public IncidentProductMetricsProjection productMetrics(String helpfulSince) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) return IncidentProductMetricsProjection.empty();
        long incidentCount = longValue(jdbc.queryForObject(
                "SELECT COUNT(1) FROM ai_ops_incident", Long.class));
        long timelineEventCount = longValue(jdbc.queryForObject(
                "SELECT COUNT(1) FROM ai_ops_incident_timeline", Long.class));
        long helpful = longValue(jdbc.queryForObject("""
                SELECT COUNT(DISTINCT i.incident_id)
                FROM ai_ops_incident i
                JOIN ai_ops_incident_timeline h ON h.incident_id=i.incident_id
                WHERE h.event_type='USER_CONFIRMED_HELPFUL'
                  AND h.create_time>=?
                  AND h.id>COALESCE((
                    SELECT MAX(r.id) FROM ai_ops_incident_timeline r
                    WHERE r.incident_id=i.incident_id AND r.event_type='INCIDENT_REOPENED'
                  ),0)
                  AND EXISTS (
                    SELECT 1 FROM ai_ops_incident_timeline v
                    WHERE v.incident_id=i.incident_id
                      AND v.event_type='VERIFICATION_SUCCEEDED'
                      AND v.id>COALESCE((
                        SELECT MAX(r2.id) FROM ai_ops_incident_timeline r2
                        WHERE r2.incident_id=i.incident_id AND r2.event_type='INCIDENT_REOPENED'
                      ),0)
                  )
                """, Long.class, helpfulSince));
        long verificationSuccess = longValue(jdbc.queryForObject("""
                SELECT COUNT(1)
                FROM ai_ops_incident i
                WHERE EXISTS (
                  SELECT 1 FROM ai_ops_incident_timeline v
                  WHERE v.incident_id=i.incident_id
                    AND v.event_type='VERIFICATION_SUCCEEDED'
                    AND v.id>COALESCE((
                      SELECT MAX(r.id) FROM ai_ops_incident_timeline r
                      WHERE r.incident_id=i.incident_id AND r.event_type='INCIDENT_REOPENED'
                    ),0)
                )
                """, Long.class));
        long comments = longValue(jdbc.queryForObject(
                "SELECT COUNT(1) FROM ai_ops_incident_timeline WHERE event_type='COMMENT'", Long.class));
        Long averageMtta = durationAverage(jdbc, """
                WITH episode AS (
                  SELECT i.incident_id,
                         i.first_seen_at,
                         COALESCE(MAX(CASE WHEN t.event_type='INCIDENT_REOPENED' THEN t.id END),0) AS start_event_id,
                         COALESCE(MAX(CASE WHEN t.event_type='INCIDENT_REOPENED' THEN t.create_time END),i.first_seen_at) AS start_time
                  FROM ai_ops_incident i
                  LEFT JOIN ai_ops_incident_timeline t ON t.incident_id=i.incident_id
                  GROUP BY i.incident_id,i.first_seen_at
                ), points AS (
                  SELECT e.incident_id,e.start_time,
                         MIN(CASE WHEN t.event_type IN ('ANALYSIS_LINKED','INVESTIGATION_STARTED') THEN t.create_time END) AS first_action
                  FROM episode e
                  LEFT JOIN ai_ops_incident_timeline t ON t.incident_id=e.incident_id AND t.id>e.start_event_id
                  GROUP BY e.incident_id,e.start_time
                )
                SELECT ROUND(AVG(TIMESTAMPDIFF(MICROSECOND,start_time,first_action)/1000))
                FROM points WHERE first_action IS NOT NULL AND first_action>=start_time
                """);
        Long averageDiagnosis = durationAverage(jdbc, """
                WITH episode AS (
                  SELECT i.incident_id,
                         COALESCE(MAX(CASE WHEN t.event_type='INCIDENT_REOPENED' THEN t.id END),0) AS start_event_id
                  FROM ai_ops_incident i
                  LEFT JOIN ai_ops_incident_timeline t ON t.incident_id=i.incident_id
                  GROUP BY i.incident_id
                ), points AS (
                  SELECT e.incident_id,
                         MIN(CASE WHEN t.event_type IN ('ANALYSIS_LINKED','INVESTIGATION_STARTED') THEN t.create_time END) AS first_action,
                         MIN(CASE WHEN t.event_type IN ('DIAGNOSIS_ACTION_REQUIRED','VERIFICATION_STARTED','VERIFICATION_SUCCEEDED') THEN t.create_time END) AS conclusion
                  FROM episode e
                  LEFT JOIN ai_ops_incident_timeline t ON t.incident_id=e.incident_id AND t.id>e.start_event_id
                  GROUP BY e.incident_id
                )
                SELECT ROUND(AVG(TIMESTAMPDIFF(MICROSECOND,first_action,conclusion)/1000))
                FROM points WHERE first_action IS NOT NULL AND conclusion IS NOT NULL AND conclusion>=first_action
                """);
        Long averageMttr = durationAverage(jdbc, """
                WITH episode AS (
                  SELECT i.incident_id,
                         i.first_seen_at,
                         COALESCE(MAX(CASE WHEN t.event_type='INCIDENT_REOPENED' THEN t.id END),0) AS start_event_id,
                         COALESCE(MAX(CASE WHEN t.event_type='INCIDENT_REOPENED' THEN t.create_time END),i.first_seen_at) AS start_time
                  FROM ai_ops_incident i
                  LEFT JOIN ai_ops_incident_timeline t ON t.incident_id=i.incident_id
                  GROUP BY i.incident_id,i.first_seen_at
                ), points AS (
                  SELECT e.incident_id,e.start_time,
                         MAX(CASE WHEN t.event_type='VERIFICATION_SUCCEEDED' THEN t.create_time END) AS verified_at
                  FROM episode e
                  LEFT JOIN ai_ops_incident_timeline t ON t.incident_id=e.incident_id AND t.id>e.start_event_id
                  GROUP BY e.incident_id,e.start_time
                )
                SELECT ROUND(AVG(TIMESTAMPDIFF(MICROSECOND,start_time,verified_at)/1000))
                FROM points WHERE verified_at IS NOT NULL AND verified_at>=start_time
                """);
        return new IncidentProductMetricsProjection(
                incidentCount,
                timelineEventCount,
                helpful,
                verificationSuccess,
                comments,
                averageMtta,
                averageDiagnosis,
                averageMttr);
    }

    private RowMapper<IncidentSnapshot> incidentMapper() {
        return (rs, rowNum) -> new IncidentSnapshot(
                rs.getLong("id"),
                rs.getString("incident_id"),
                rs.getString("project_id"),
                rs.getString("title"),
                IncidentStatus.fromStored(rs.getString("status")),
                rs.getString("severity"),
                rs.getString("service_name"),
                rs.getString("source_type"),
                rs.getString("fingerprint"),
                rs.getString("dedup_key"),
                rs.getString("current_run_id"),
                rs.getString("owner_user_id"),
                rs.getString("summary"),
                rs.getString("labels_json"),
                rs.getString("metadata_json"),
                rs.getLong("occurrence_count"),
                rs.getString("affected_resources_json"),
                rs.getString("first_seen_at_text"),
                rs.getString("last_seen_at_text"),
                rs.getString("create_time_text"),
                rs.getString("update_time_text"),
                rs.getString("acknowledged_at_text"),
                rs.getString("resolved_at_text"),
                rs.getString("reviewed_at_text"));
    }

    private RowMapper<IncidentTimelineEntry> timelineMapper() {
        return (rs, rowNum) -> new IncidentTimelineEntry(
                rs.getLong("id"),
                rs.getString("incident_id"),
                rs.getString("event_type"),
                rs.getString("title"),
                rs.getString("detail"),
                rs.getString("actor"),
                rs.getString("ref_type"),
                rs.getString("ref_id"),
                rs.getString("payload_json"),
                rs.getString("create_time"));
    }

    private IncidentSnapshot fallback(IncidentDraft draft) {
        return new IncidentSnapshot(
                null,
                draft.incidentId(),
                draft.projectId(),
                draft.title(),
                draft.status(),
                draft.severity(),
                draft.serviceName(),
                draft.sourceType(),
                "",
                "",
                "",
                "",
                draft.summary(),
                JSON.toJSONString(draft.labels()),
                JSON.toJSONString(draft.metadata()),
                1L,
                JSON.toJSONString(draft.affectedResources()),
                "", "", "", "", "", "", "");
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private long longValue(Long value) {
        return value == null ? 0L : value;
    }

    private Long durationAverage(JdbcTemplate jdbc, String sql) {
        Number value = jdbc.queryForObject(sql, Number.class);
        return value == null ? null : value.longValue();
    }

    private Object blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private JdbcTemplate jdbc() {
        return jdbcTemplateProvider.getIfAvailable();
    }
}
