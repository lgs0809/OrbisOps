package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisTaskReadRepository;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskIncident;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskSnapshot;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcAnalysisTaskReadRepository implements IAnalysisTaskReadRepository {

    private static final String TASK_COLUMNS = """
            run_id,project_id,session_id,user_id,agent_id,agent_version,agent_definition_hash,
            execution_harness,status,request_json,response_json,error_message,created_at,updated_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAnalysisTaskReadRepository(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AnalysisTaskSnapshot> list(String projectId, String status, int limit) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(TASK_COLUMNS)
                .append(" FROM ai_ops_agent_run WHERE project_id=?");
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        if (!text(status).isBlank()) {
            sql.append(" AND status=?");
            args.add(status);
        }
        sql.append(" ORDER BY updated_at DESC, id DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream()
                .map(this::snapshot)
                .toList();
    }

    @Override
    public Optional<AnalysisTaskSnapshot> find(String projectId, String runId) {
        return jdbcTemplate.queryForList(
                        "SELECT " + TASK_COLUMNS + " FROM ai_ops_agent_run WHERE project_id=? AND run_id=? LIMIT 1",
                        projectId,
                        runId)
                .stream()
                .findFirst()
                .map(this::snapshot);
    }

    @Override
    public List<AnalysisTaskIncident> findIncidents(String projectId, String runId) {
        return jdbcTemplate.queryForList("""
                        SELECT i.incident_id,i.title,i.status,i.severity,i.service_name,i.occurrence_count,
                               i.first_seen_at,i.last_seen_at
                        FROM ai_ops_incident_run link
                        JOIN ai_ops_incident i ON i.incident_id=link.incident_id
                        WHERE link.run_id=? AND i.project_id=? ORDER BY i.update_time DESC
                        """, runId, projectId)
                .stream()
                .map(row -> new AnalysisTaskIncident(
                        text(row.get("incident_id")),
                        text(row.get("title")),
                        text(row.get("status")),
                        text(row.get("severity")),
                        text(row.get("service_name")),
                        longValue(row.get("occurrence_count")),
                        instant(row.get("first_seen_at")),
                        instant(row.get("last_seen_at"))))
                .toList();
    }

    private AnalysisTaskSnapshot snapshot(Map<String, Object> row) {
        return new AnalysisTaskSnapshot(
                text(row.get("run_id")),
                text(row.get("project_id")),
                text(row.get("session_id")),
                text(row.get("user_id")),
                text(row.get("agent_id")),
                integer(row.get("agent_version")),
                text(row.get("agent_definition_hash")),
                text(row.get("execution_harness")),
                text(row.get("status")),
                parseMap(row.get("request_json")),
                parseMap(row.get("response_json")),
                text(row.get("error_message")),
                instant(row.get("created_at")),
                instant(row.get("updated_at")));
    }

    private Map<String, Object> parseMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        String json = text(value);
        if (json.isBlank()) return Map.of();
        try {
            Map<String, Object> parsed = JSON.parseObject(
                    json, new TypeReference<LinkedHashMap<String, Object>>() { });
            return parsed == null ? Map.of() : parsed;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.util.Date date) return date.toInstant();
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        try {
            return Instant.parse(text(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(text(value));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
