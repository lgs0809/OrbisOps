package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.memory.adapter.repository.IContextMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySearchCriteria;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC persistence owner for user/project Context Memory. */
@Repository
public class JdbcContextMemoryRepository implements IContextMemoryRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, memory_id, scope_type, scope_id, memory_type, title, summary, content, keywords,
                   status, confidence, source_type, source_id, source_message_hash, created_by,
                   create_time, update_time, expire_time
            FROM ai_ops_context_memory
            """;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final JdbcContextMemorySchemaInitializer schemaInitializer;

    public JdbcContextMemoryRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            JdbcContextMemorySchemaInitializer schemaInitializer) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.schemaInitializer = schemaInitializer;
    }

    @Override
    public boolean available() {
        return jdbcTemplateProvider.getIfAvailable() != null;
    }

    @Override
    public List<ContextMemorySnapshot> search(ContextMemorySearchCriteria criteria) {
        ContextMemorySearchCriteria safeCriteria = criteria == null
                ? new ContextMemorySearchCriteria("", "", "", "", 100)
                : criteria;
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        append(sql, args, "scope_type", safeCriteria.scopeType());
        append(sql, args, "scope_id", safeCriteria.scopeId());
        append(sql, args, "memory_type", safeCriteria.memoryType());
        append(sql, args, "status", safeCriteria.status());
        sql.append(" ORDER BY update_time DESC, id DESC LIMIT ?");
        args.add(safeCriteria.limit());
        return template.query(sql.toString(), this::snapshot, args.toArray());
    }

    @Override
    public Optional<ContextMemorySnapshot> findByMemoryId(String memoryId) {
        if (!hasText(memoryId)) return Optional.empty();
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        List<ContextMemorySnapshot> rows = template.query(
                SELECT_COLUMNS + " WHERE memory_id = ? LIMIT 1",
                this::snapshot,
                memoryId.trim());
        return rows.stream().findFirst();
    }

    @Override
    public boolean exists(String memoryId) {
        if (!hasText(memoryId)) return false;
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        Integer count = template.queryForObject(
                "SELECT COUNT(1) FROM ai_ops_context_memory WHERE memory_id = ?",
                Integer.class,
                memoryId.trim());
        return count != null && count > 0;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void upsert(ContextMemorySnapshot snapshot) {
        if (snapshot == null) return;
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        template.update("""
                        INSERT INTO ai_ops_context_memory
                          (memory_id, scope_type, scope_id, memory_type, title, summary, content, keywords,
                           status, confidence, source_type, source_id, source_message_hash, created_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          scope_type=VALUES(scope_type),
                          scope_id=VALUES(scope_id),
                          memory_type=VALUES(memory_type),
                          title=VALUES(title),
                          summary=VALUES(summary),
                          content=VALUES(content),
                          keywords=VALUES(keywords),
                          status=VALUES(status),
                          confidence=VALUES(confidence),
                          source_type=VALUES(source_type),
                          source_id=VALUES(source_id),
                          source_message_hash=VALUES(source_message_hash)
                        """,
                snapshot.memoryId(),
                snapshot.scopeType(),
                snapshot.scopeId(),
                snapshot.memoryType(),
                snapshot.title(),
                snapshot.summary(),
                snapshot.content(),
                snapshot.keywords(),
                snapshot.status(),
                snapshot.confidence(),
                snapshot.sourceType(),
                snapshot.sourceId(),
                snapshot.sourceMessageHash(),
                snapshot.createdBy());
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public boolean updateStatus(String memoryId, String status) {
        if (!hasText(memoryId)) return false;
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        return template.update("""
                UPDATE ai_ops_context_memory
                SET status = ?
                WHERE memory_id = ?
                """, status, memoryId.trim()) > 0;
    }

    private ContextMemorySnapshot snapshot(ResultSet resultSet, int rowNum) throws SQLException {
        return new ContextMemorySnapshot(
                resultSet.getLong("id"),
                resultSet.getString("memory_id"),
                resultSet.getString("scope_type"),
                resultSet.getString("scope_id"),
                resultSet.getString("memory_type"),
                resultSet.getString("title"),
                resultSet.getString("summary"),
                resultSet.getString("content"),
                resultSet.getString("keywords"),
                resultSet.getString("status"),
                resultSet.getBigDecimal("confidence"),
                resultSet.getString("source_type"),
                resultSet.getString("source_id"),
                resultSet.getString("source_message_hash"),
                resultSet.getString("created_by"),
                timestamp(resultSet.getTimestamp("create_time")),
                timestamp(resultSet.getTimestamp("update_time")),
                timestamp(resultSet.getTimestamp("expire_time")));
    }

    private void append(StringBuilder sql, List<Object> args, String column, String value) {
        if (!hasText(value)) return;
        sql.append(" AND ").append(column).append(" = ?");
        args.add(value.trim());
    }

    private String timestamp(Timestamp value) {
        return value == null ? "" : String.valueOf(value);
    }

    private JdbcTemplate requiredTemplate() {
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) throw new IllegalStateException("CONTEXT_MEMORY_STORE_UNAVAILABLE");
        return template;
    }

    private void ensureSchema() {
        if (schemaInitializer != null) schemaInitializer.initialize();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
