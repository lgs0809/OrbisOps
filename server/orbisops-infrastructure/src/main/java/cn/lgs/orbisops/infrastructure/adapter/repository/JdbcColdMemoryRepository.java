package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.memory.adapter.repository.IColdMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC persistence owner for conversation messages and durable cold memory items. */
@Repository
public class JdbcColdMemoryRepository implements IColdMemoryRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final JdbcConversationMemorySchemaInitializer schemaInitializer;

    public JdbcColdMemoryRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            JdbcConversationMemorySchemaInitializer schemaInitializer) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.schemaInitializer = schemaInitializer;
    }

    @Override
    public boolean available() {
        return jdbcTemplateProvider.getIfAvailable() != null;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void appendMessage(ColdMemoryMessageSnapshot message) {
        if (message == null) return;
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        template.update("""
                        INSERT INTO ai_ops_chat_message
                        (session_id, user_id, role, content, metadata)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                message.sessionId(),
                message.userId(),
                message.role(),
                message.content(),
                JSON.toJSONString(message.metadata()));
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void saveItems(List<ColdMemoryItemSnapshot> items) {
        if (items == null || items.isEmpty()) return;
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        for (ColdMemoryItemSnapshot item : items) {
            if (item == null || !hasText(item.sessionId()) || !hasText(item.content())) continue;
            template.update("""
                            INSERT INTO ai_ops_memory_item
                            (session_id, user_id, memory_type, content, importance, tags_json,
                             source_message_role, source_message_hash, metadata)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    item.sessionId(),
                    item.userId(),
                    value(item.memoryType(), "fact"),
                    item.content(),
                    item.importance(),
                    item.tagsJson(),
                    item.sourceMessageRole(),
                    item.sourceMessageHash(),
                    JSON.toJSONString(item.metadata()));
        }
    }

    @Override
    public List<ColdMemoryItemSnapshot> listItems(String sessionId, String userId, int limit) {
        if (!hasText(sessionId)) return List.of();
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        StringBuilder sql = new StringBuilder("""
                SELECT session_id, user_id, memory_type, content, importance, tags_json,
                       source_message_role, source_message_hash, metadata,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time
                FROM ai_ops_memory_item
                WHERE session_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(sessionId.trim());
        if (hasText(userId)) {
            sql.append(" AND (user_id = ? OR user_id IS NULL OR user_id = '')");
            args.add(userId.trim());
        }
        sql.append(" ORDER BY importance DESC, id DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 500)));
        return template.query(sql.toString(), (resultSet, rowNum) -> new ColdMemoryItemSnapshot(
                resultSet.getString("session_id"),
                resultSet.getString("user_id"),
                resultSet.getString("memory_type"),
                resultSet.getString("content"),
                resultSet.getBigDecimal("importance"),
                resultSet.getString("tags_json"),
                resultSet.getString("source_message_role"),
                resultSet.getString("source_message_hash"),
                metadata(resultSet.getString("metadata")),
                resultSet.getString("create_time")), args.toArray());
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void clear(String sessionId) {
        if (!hasText(sessionId)) return;
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        // Fence pending/late workers before clearing their source. Keep the sequence monotonic.
        template.update("""
                UPDATE ai_ops_conversation_memory_state SET covered_seq=last_seq, summary_revision=summary_revision+1,
                    summary_content='', protected_messages_json='[]' WHERE session_id=?
                """, sessionId.trim());
        template.update("UPDATE ai_ops_memory_post_processing SET status='CANCELED', epoch=epoch+1 WHERE session_id=? AND status<>'COMPLETED'", sessionId.trim());
        template.update("DELETE FROM ai_ops_chat_message WHERE session_id = ?", sessionId.trim());
        template.update("DELETE FROM ai_ops_memory_item WHERE session_id = ?", sessionId.trim());
    }

    private Map<String, Object> metadata(String raw) {
        if (!hasText(raw)) return Map.of();
        try {
            JSONObject parsed = JSON.parseObject(raw);
            return parsed == null ? Map.of() : new LinkedHashMap<>(parsed);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private JdbcTemplate requiredTemplate() {
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) throw new IllegalStateException("COLD_MEMORY_STORE_UNAVAILABLE");
        return template;
    }

    private void ensureSchema() {
        if (schemaInitializer != null) schemaInitializer.initialize();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String value(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }
}
