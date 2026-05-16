package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.evidence.adapter.repository.IEvidenceRepository;
import cn.lgs.orbisops.domain.evidence.model.EvidenceRecord;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@DependsOn("jdbcEvidenceSchemaInitializer")
public class JdbcEvidenceRepository implements IEvidenceRepository {

    private final JdbcTemplate jdbc;

    public JdbcEvidenceRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider) {
        this.jdbc = provider.getIfAvailable();
    }

    @Override
    public EvidenceRecord save(EvidenceRecord evidence) {
        if (evidence == null) throw new IllegalArgumentException("EVIDENCE_REQUIRED");
        try {
            requireJdbc().update("""
                    INSERT INTO ai_ops_evidence
                      (evidence_id, project_id, run_id, source_type, source_id, tool_result_id, output_hash,
                       full_output_ref, summary, verified, metadata_json, idempotency_key, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      summary=VALUES(summary), verified=VALUES(verified), metadata_json=VALUES(metadata_json)
                    """,
                    evidence.evidenceId(), evidence.projectId(), evidence.runId(), evidence.sourceType(),
                    evidence.sourceId(), evidence.toolResultId(), evidence.outputHash(), evidence.fullOutputRef(),
                    evidence.summary(), evidence.verified() ? 1 : 0, JSON.toJSONString(evidence.metadata()),
                    evidence.idempotencyKey(), evidence.createdBy());
            return findByIdempotencyKey(evidence.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Evidence 持久化后无法读取"));
        } catch (DataAccessException e) {
            throw new IllegalStateException("Evidence 持久化失败，工具执行必须 fail closed：" + e.getMessage(), e);
        }
    }

    @Override
    public Optional<EvidenceRecord> findByIdempotencyKey(String idempotencyKey) {
        return first(requireJdbc().queryForList(
                "SELECT * FROM ai_ops_evidence WHERE idempotency_key=? LIMIT 1", value(idempotencyKey)));
    }

    @Override
    public Optional<EvidenceRecord> findScoped(String evidenceId, String projectId, String runId) {
        return first(requireJdbc().queryForList("""
                SELECT * FROM ai_ops_evidence
                WHERE evidence_id=? AND project_id=? AND run_id=? LIMIT 1
                """, value(evidenceId), value(projectId), value(runId)));
    }

    @Override
    public List<EvidenceRecord> listForRun(String projectId, String runId, int limit) {
        return requireJdbc().queryForList("""
                SELECT * FROM ai_ops_evidence
                WHERE project_id=? AND run_id=? ORDER BY id DESC LIMIT ?
                """, value(projectId), value(runId), limit).stream().map(this::map).toList();
    }

    @Override
    public long count() {
        Long count = requireJdbc().queryForObject("SELECT COUNT(1) FROM ai_ops_evidence", Long.class);
        return count == null ? 0L : count;
    }

    private Optional<EvidenceRecord> first(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(map(rows.get(0)));
    }

    @SuppressWarnings("unchecked")
    private EvidenceRecord map(Map<String, Object> row) {
        Map<String, Object> metadata;
        try {
            String json = value(row.get("metadata_json"));
            metadata = json.isBlank() ? Map.of() : JSON.parseObject(json, Map.class);
        } catch (Exception ignored) {
            metadata = Map.of();
        }
        return new EvidenceRecord(
                value(row.get("evidence_id")), value(row.get("project_id")), value(row.get("run_id")),
                value(row.get("source_type")), value(row.get("source_id")), value(row.get("tool_result_id")),
                value(row.get("output_hash")), value(row.get("full_output_ref")), value(row.get("summary")),
                bool(row.get("verified")), metadata, value(row.get("idempotency_key")),
                value(row.get("created_by")), time(row.get("created_at")));
    }

    private JdbcTemplate requireJdbc() {
        if (jdbc == null) throw new IllegalStateException("Evidence Store 数据库未配置，工具执行必须 fail closed");
        return jdbc;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean bool(Object value) {
        if (value instanceof Number number) return number.intValue() != 0;
        return Boolean.parseBoolean(this.value(value));
    }

    private String time(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().toString();
        String normalized = this.value(value);
        return normalized.isBlank() ? "1970-01-01T00:00:00Z" : normalized;
    }
}
