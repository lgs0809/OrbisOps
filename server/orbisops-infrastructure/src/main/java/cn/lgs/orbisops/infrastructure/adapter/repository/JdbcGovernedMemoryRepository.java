package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.memory.adapter.repository.IGovernedMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.GovernedMemoryHead;
import cn.lgs.orbisops.domain.memory.model.GovernedMemoryRuntimeQuery;
import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;
import cn.lgs.orbisops.domain.memory.model.GovernedMemoryVersionSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryScope;
import cn.lgs.orbisops.domain.memory.model.MemoryType;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** JDBC persistence owner for governed explicit memories and their governance records. */
@Repository
public class JdbcGovernedMemoryRepository implements IGovernedMemoryRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, memory_id, scope_type, scope_id, user_id, project_id, agent_id, session_id, memory_type,
                   logical_key, content, normalized_content, source_type, source_run_id, verified, confidence,
                   risk_level, status, version, memory_hash, proof_refs_json, expires_at, created_by,
                   idempotency_key, create_time, update_time
            FROM ai_ops_memory
            """;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final JdbcGovernedMemorySchemaInitializer schemaInitializer;

    public JdbcGovernedMemoryRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            JdbcGovernedMemorySchemaInitializer schemaInitializer) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.schemaInitializer = schemaInitializer;
    }

    @Override
    public <T> T withLogicalLock(String lockKey, Supplier<T> action) {
        JdbcTemplate template = requiredTemplate();
        return new TransactionTemplate(new DataSourceTransactionManager(template.getDataSource())).execute(status -> {
            template.update("INSERT INTO ai_ops_memory_logical_lock(lock_key) VALUES (?) ON DUPLICATE KEY UPDATE lock_key=VALUES(lock_key)", lockKey);
            template.queryForObject("SELECT lock_key FROM ai_ops_memory_logical_lock WHERE lock_key=? FOR UPDATE", String.class, lockKey);
            return action.get();
        });
    }

    @Override
    public void appendSource(String memoryId, String idempotencyKey, String sourceRunId,
                             List<Map<String, Object>> proofRefs, String actor) {
        requiredTemplate().update("""
                INSERT INTO ai_ops_memory_source(idempotency_key, memory_id, source_run_id, proof_refs_json, created_by)
                VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE idempotency_key=VALUES(idempotency_key)
                """, idempotencyKey, memoryId, sourceRunId == null ? "" : sourceRunId,
                JSON.toJSONString(proofRefs), actor == null ? "" : actor);
    }

    @Override
    public boolean available() {
        return jdbcTemplateProvider.getIfAvailable() != null;
    }

    @Override
    public Optional<GovernedMemorySnapshot> findByIdempotencyKey(String idempotencyKey) {
        if (!hasText(idempotencyKey)) return Optional.empty();
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        return template.query(
                        SELECT_COLUMNS + " WHERE idempotency_key = ? OR memory_id IN"
                                + " (SELECT memory_id FROM ai_ops_memory_source WHERE idempotency_key=?) LIMIT 1",
                        this::snapshot,
                        idempotencyKey.trim(),
                        idempotencyKey.trim())
                .stream()
                .findFirst();
    }

    @Override
    public Optional<GovernedMemoryHead> findLatestHead(
            MemoryScope scope,
            String scopeId,
            MemoryType type,
            String logicalKey) {
        if (scope == null || type == null || !hasText(scopeId) || !hasText(logicalKey)) {
            return Optional.empty();
        }
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        return template.query("""
                        SELECT memory_id, version, memory_hash
                        FROM ai_ops_memory
                        WHERE scope_type = ? AND scope_id = ? AND memory_type = ? AND logical_key = ?
                          AND status IN ('ACTIVE','CONFLICT')
                        ORDER BY version DESC, id DESC LIMIT 1
                        """,
                (resultSet, rowNum) -> new GovernedMemoryHead(
                        resultSet.getString("memory_id"),
                        resultSet.getInt("version"),
                        resultSet.getString("memory_hash")),
                scope.name(),
                scopeId.trim(),
                type.name(),
                logicalKey.trim()).stream().findFirst();
    }

    @Override
    public Optional<GovernedMemorySnapshot> findByMemoryId(String memoryId) {
        if (!hasText(memoryId)) return Optional.empty();
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        return template.query(
                        SELECT_COLUMNS + " WHERE memory_id = ? LIMIT 1",
                        this::snapshot,
                        memoryId.trim())
                .stream()
                .findFirst();
    }

    @Override
    public List<GovernedMemorySnapshot> selectForRuntime(GovernedMemoryRuntimeQuery query) {
        GovernedMemoryRuntimeQuery safe = query == null
                ? new GovernedMemoryRuntimeQuery("", "", "", "", 1)
                : query;
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append("""
                 WHERE status = 'ACTIVE'
                   AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                   AND ((scope_type = 'USER' AND scope_id = ?)
                     OR (scope_type = 'PROJECT' AND scope_id = ?)
                     OR (scope_type = 'SESSION' AND scope_id = ?))
                """);
        List<Object> args = new ArrayList<>();
        args.add(safe.userId());
        args.add(safe.projectId());
        args.add(safe.sessionId());
        if (hasText(safe.excludedSourceRunId())) {
            sql.append(" AND source_run_id <> ?");
            args.add(safe.excludedSourceRunId());
            sql.append(" AND NOT EXISTS (SELECT 1 FROM ai_ops_memory_source ms WHERE ms.memory_id=ai_ops_memory.memory_id AND ms.source_run_id=?)");
            args.add(safe.excludedSourceRunId());
        }
        sql.append(" ORDER BY verified DESC, update_time DESC, id DESC LIMIT ?");
        args.add(safe.limit());
        return template.query(sql.toString(), this::snapshot, args.toArray());
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void insert(GovernedMemorySnapshot snapshot) {
        if (snapshot == null) return;
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        template.update("""
                INSERT INTO ai_ops_memory
                  (memory_id, scope_type, scope_id, user_id, project_id, agent_id, session_id, memory_type,
                   logical_key, content, normalized_content, source_type, source_run_id, verified, confidence,
                   risk_level, status, version, memory_hash, proof_refs_json, expires_at, created_by, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                snapshot.memoryId(),
                snapshot.scope().name(),
                snapshot.scopeId(),
                snapshot.userId(),
                snapshot.projectId(),
                snapshot.agentId(),
                snapshot.sessionId(),
                snapshot.type().name(),
                snapshot.logicalKey(),
                snapshot.content(),
                snapshot.normalizedContent(),
                snapshot.sourceType(),
                snapshot.sourceRunId(),
                snapshot.verified() ? 1 : 0,
                snapshot.confidence(),
                snapshot.riskLevel(),
                snapshot.status(),
                snapshot.version(),
                snapshot.memoryHash(),
                JSON.toJSONString(snapshot.proofRefs()),
                timestamp(snapshot.expiresAt()),
                snapshot.createdBy(),
                snapshot.idempotencyKey());
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void appendVersion(GovernedMemoryVersionSnapshot version) {
        if (version == null) return;
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        template.update("""
                INSERT INTO ai_ops_memory_version
                  (memory_id, version, memory_hash, status, content, normalized_content, source_run_id, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                version.memoryId(),
                version.version(),
                version.memoryHash(),
                version.status(),
                version.content(),
                version.normalizedContent(),
                version.sourceRunId(),
                version.createdBy());
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public boolean markConflict(String memoryId) {
        if (!hasText(memoryId)) return false;
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        return template.update("""
                UPDATE ai_ops_memory
                SET status = 'CONFLICT', update_time = CURRENT_TIMESTAMP
                WHERE memory_id = ?
                """, memoryId.trim()) == 1;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public String recordConflict(
            MemoryScope scope,
            String scopeId,
            String logicalKey,
            String existingMemoryId,
            String incomingMemoryId,
            String actor) {
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        String conflictId = "mem-conflict-" + UUID.randomUUID();
        template.update("""
                INSERT INTO ai_ops_memory_conflict
                  (conflict_id, scope_type, scope_id, logical_key, existing_memory_id, incoming_memory_id,
                   status, resolution_json, created_by)
                VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?, ?)
                """,
                conflictId,
                scope == null ? "" : scope.name(),
                value(scopeId),
                value(logicalKey),
                value(existingMemoryId),
                value(incomingMemoryId),
                JSON.toJSONString(Map.of("reason", "EXPLICIT_MEMORY_VALUE_CHANGED")),
                value(actor));
        return conflictId;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public boolean verifyProjectFact(String memoryId, List<Map<String, Object>> proofRefs) {
        if (!hasText(memoryId)) return false;
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        return template.update("""
                UPDATE ai_ops_memory
                SET verified = 1,
                    confidence = GREATEST(confidence, 0.9000),
                    proof_refs_json = ?,
                    update_time = CURRENT_TIMESTAMP
                WHERE memory_id = ? AND status = 'ACTIVE'
                """,
                JSON.toJSONString(proofRefs == null ? List.of() : proofRefs),
                memoryId.trim()) == 1;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void recordAudit(
            String memoryId,
            String action,
            String actor,
            GovernedMemorySnapshot snapshot,
            String conflictId) {
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        template.update("""
                INSERT INTO ai_ops_memory_audit
                  (audit_id, memory_id, action, actor, payload_json)
                VALUES (?, ?, ?, ?, ?)
                """,
                "memory-audit-" + UUID.randomUUID(),
                value(memoryId),
                value(action),
                value(actor),
                JSON.toJSONString(auditPayload(snapshot, conflictId)));
    }

    private GovernedMemorySnapshot snapshot(ResultSet resultSet, int rowNum) throws SQLException {
        return new GovernedMemorySnapshot(
                resultSet.getLong("id"),
                resultSet.getString("memory_id"),
                MemoryScope.require(resultSet.getString("scope_type")),
                resultSet.getString("scope_id"),
                resultSet.getString("user_id"),
                resultSet.getString("project_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("session_id"),
                MemoryType.require(resultSet.getString("memory_type")),
                resultSet.getString("logical_key"),
                resultSet.getString("content"),
                resultSet.getString("normalized_content"),
                resultSet.getString("source_type"),
                resultSet.getString("source_run_id"),
                resultSet.getBoolean("verified"),
                resultSet.getDouble("confidence"),
                resultSet.getString("risk_level"),
                resultSet.getString("status"),
                resultSet.getInt("version"),
                resultSet.getString("memory_hash"),
                proofRefs(resultSet.getString("proof_refs_json")),
                instant(resultSet.getTimestamp("expires_at")),
                resultSet.getString("created_by"),
                resultSet.getString("idempotency_key"),
                instant(resultSet.getTimestamp("create_time")),
                instant(resultSet.getTimestamp("update_time")));
    }

    private Map<String, Object> auditPayload(
            GovernedMemorySnapshot snapshot,
            String conflictId) {
        if (snapshot == null) return Map.of();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", snapshot.databaseId());
        payload.put("memoryId", snapshot.memoryId());
        payload.put("scopeType", snapshot.scope().name());
        payload.put("scopeId", snapshot.scopeId());
        payload.put("userId", snapshot.userId());
        payload.put("projectId", snapshot.projectId());
        payload.put("agentId", snapshot.agentId());
        payload.put("sessionId", snapshot.sessionId());
        payload.put("memoryType", snapshot.type().name());
        payload.put("logicalKey", snapshot.logicalKey());
        payload.put("content", snapshot.content());
        payload.put("normalizedContent", snapshot.normalizedContent());
        payload.put("sourceType", snapshot.sourceType());
        payload.put("sourceRunId", snapshot.sourceRunId());
        payload.put("verified", snapshot.verified() ? 1 : 0);
        payload.put("confidence", snapshot.confidence());
        payload.put("riskLevel", snapshot.riskLevel());
        payload.put("status", snapshot.status());
        payload.put("version", snapshot.version());
        payload.put("memoryHash", snapshot.memoryHash());
        payload.put("proofRefsJson", JSON.toJSONString(snapshot.proofRefs()));
        payload.put("expiresAt", text(snapshot.expiresAt()));
        payload.put("createdBy", snapshot.createdBy());
        payload.put("idempotencyKey", snapshot.idempotencyKey());
        payload.put("createTime", text(snapshot.createdAt()));
        payload.put("updateTime", text(snapshot.updatedAt()));
        if (hasText(conflictId)) payload.put("conflictId", conflictId.trim());
        return payload;
    }

    private List<Map<String, Object>> proofRefs(String payload) {
        if (!hasText(payload)) return List.of();
        List<Object> parsed = JSON.parseArray(payload, Object.class);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : parsed) {
            if (!(item instanceof Map<?, ?> values)) continue;
            Map<String, Object> copy = new LinkedHashMap<>();
            values.forEach((key, value) -> copy.put(String.valueOf(key), value));
            result.add(copy);
        }
        return result;
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private String text(Instant value) {
        return value == null ? null : value.toString();
    }

    private JdbcTemplate requiredTemplate() {
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) throw new IllegalStateException("Memory Store 数据库未配置");
        return template;
    }

    private void ensureSchema() {
        if (schemaInitializer != null) schemaInitializer.initialize();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
