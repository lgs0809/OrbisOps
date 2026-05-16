package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.chatsession.adapter.repository.IChatSessionRepository;
import cn.lgs.orbisops.domain.chatsession.model.ChatMessageSnapshot;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipant;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantDraft;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantReplacement;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSearchCriteria;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSnapshot;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionUpdate;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JDBC persistence owner for Chat Session state, participant ACL and message read models. */
@Repository
public class JdbcChatSessionRepository implements IChatSessionRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final JdbcChatSessionSchemaInitializer schemaInitializer;
    private final JdbcConversationMemorySchemaInitializer conversationMemorySchemaInitializer;

    public JdbcChatSessionRepository(
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            JdbcChatSessionSchemaInitializer schemaInitializer) {
        this(jdbcTemplateProvider, schemaInitializer, null);
    }

    @Autowired
    public JdbcChatSessionRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            JdbcChatSessionSchemaInitializer schemaInitializer,
            JdbcConversationMemorySchemaInitializer conversationMemorySchemaInitializer) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.schemaInitializer = schemaInitializer;
        this.conversationMemorySchemaInitializer = conversationMemorySchemaInitializer;
    }

    @Override
    public boolean available() {
        return jdbcTemplateProvider.getIfAvailable() != null;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void insert(ChatSessionSnapshot session) {
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        template.update("""
                        INSERT INTO ai_ops_chat_session
                        (session_id, user_id, project_id, agent_id, agent_binding_mode, agent_version,
                         agent_definition_hash, title, mode, engine, rag_enabled, knowledge_base_id,
                         status, state_version, metadata)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                session.sessionId(), session.userId(), session.projectId(), session.agentId(),
                session.agentBindingMode(), session.agentVersion(), session.agentDefinitionHash(),
                session.title(), session.mode(), session.engine(), session.ragEnabled() ? 1 : 0,
                session.knowledgeBaseId(), session.status(), session.stateVersion(),
                JSON.toJSONString(session.metadata()));
        ensureOwnerParticipant(template, session);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void insertIfAbsent(ChatSessionSnapshot session) {
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        template.update("""
                        INSERT INTO ai_ops_chat_session
                        (session_id, user_id, project_id, agent_id, agent_binding_mode, agent_version,
                         agent_definition_hash, title, mode, engine, rag_enabled, knowledge_base_id,
                         status, state_version, metadata)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE last_active_at = CURRENT_TIMESTAMP
                        """,
                session.sessionId(), session.userId(), session.projectId(), session.agentId(),
                session.agentBindingMode(), session.agentVersion(), session.agentDefinitionHash(),
                session.title(), session.mode(), session.engine(), session.ragEnabled() ? 1 : 0,
                session.knowledgeBaseId(), session.status(), session.stateVersion(),
                JSON.toJSONString(session.metadata()));
        ensureOwnerParticipant(template, session);
    }

    @Override
    public Optional<ChatSessionSnapshot> findById(String sessionId) {
        if (!hasText(sessionId)) return Optional.empty();
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        List<ChatSessionSnapshot> rows = template.query(baseSelect() + """
                        WHERE s.session_id = ?
                        LIMIT 1
                        """,
                this::mapSession,
                sessionId.trim());
        return rows.stream().findFirst();
    }

    @Override
    public List<ChatSessionSnapshot> search(ChatSessionSearchCriteria criteria) {
        ChatSessionSearchCriteria safe = criteria == null
                ? new ChatSessionSearchCriteria("", "", "", null, 100)
                : criteria;
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        StringBuilder sql = new StringBuilder(baseSelect()).append(" WHERE s.status <> 'DELETED'");
        List<Object> args = new ArrayList<>();
        if (hasText(safe.userId())) {
            sql.append(" AND (s.user_id = ? OR EXISTS (SELECT 1 FROM ai_ops_chat_session_participant sp WHERE sp.session_id=s.session_id AND sp.participant_user_id=? AND sp.status='ACTIVE'))");
            args.add(safe.userId());
            args.add(safe.userId());
        }
        if (hasText(safe.agentId())) {
            sql.append(" AND s.agent_id = ?");
            args.add(safe.agentId());
        }
        if (safe.favorite() != null) {
            String expression = "(COALESCE(s.metadata, '') LIKE ? OR COALESCE(s.metadata, '') LIKE ?)";
            sql.append(Boolean.TRUE.equals(safe.favorite()) ? " AND " + expression : " AND NOT " + expression);
            args.add("%\"favorite\":true%");
            args.add("%favorite%true%");
        }
        if (hasText(safe.keyword())) {
            sql.append("""
                     AND (
                       LOWER(s.title) LIKE ?
                       OR LOWER(s.agent_id) LIKE ?
                       OR LOWER(s.session_id) LIKE ?
                       OR EXISTS (
                         SELECT 1 FROM ai_ops_chat_message sm
                         WHERE sm.session_id = s.session_id
                           AND LOWER(sm.content) LIKE ?
                       )
                     )
                    """);
            String like = "%" + safe.keyword() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY s.last_active_at DESC, s.id DESC LIMIT ?");
        args.add(safe.limit());
        return template.query(sql.toString(), this::mapSession, args.toArray());
    }

    @Override
    public List<ChatMessageSnapshot> messages(String sessionId, int limit) {
        if (!hasText(sessionId)) return List.of();
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        return template.query("""
                        SELECT recent.id, recent.session_id, recent.user_id, recent.role,
                               recent.content, recent.metadata,
                               DATE_FORMAT(recent.create_time, '%Y-%m-%d %H:%i:%s') AS create_time
                        FROM (
                            SELECT id, session_id, user_id, role, content, metadata, create_time
                            FROM ai_ops_chat_message
                            WHERE session_id = ?
                            ORDER BY id DESC
                            LIMIT ?
                        ) recent
                        ORDER BY recent.id ASC
                        """,
                (resultSet, rowNum) -> new ChatMessageSnapshot(
                        String.valueOf(resultSet.getLong("id")),
                        resultSet.getString("session_id"),
                        resultSet.getString("user_id"),
                        resultSet.getString("role"),
                        resultSet.getString("content"),
                        resultSet.getString("create_time"),
                        metadata(resultSet.getString("metadata"))),
                sessionId.trim(),
                Math.max(1, Math.min(limit, 500)));
    }

    @Override
    public boolean update(ChatSessionUpdate update) {
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        return template.update("""
                        UPDATE ai_ops_chat_session
                        SET title = COALESCE(NULLIF(?, ''), title),
                            status = COALESCE(NULLIF(?, ''), status),
                            metadata = ?,
                            state_version = state_version + 1,
                            last_active_at = CURRENT_TIMESTAMP
                        WHERE session_id = ? AND state_version = ?
                        """,
                value(update.title()), value(update.status()), JSON.toJSONString(update.metadata()),
                update.sessionId(), update.expectedStateVersion()) == 1;
    }

    @Override
    public boolean hasActiveParticipant(String sessionId, String userId) {
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        Integer count = template.queryForObject("""
                SELECT COUNT(1) FROM ai_ops_chat_session_participant
                WHERE session_id=? AND participant_user_id=? AND status='ACTIVE'
                """, Integer.class, sessionId, userId);
        return count != null && count > 0;
    }

    @Override
    public Optional<String> activeParticipantRole(String sessionId, String userId) {
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        List<String> roles = template.queryForList("""
                SELECT participant_role FROM ai_ops_chat_session_participant
                WHERE session_id=? AND participant_user_id=? AND status='ACTIVE' LIMIT 1
                """, String.class, sessionId, userId);
        return roles.stream().findFirst();
    }

    @Override
    public List<ChatSessionParticipant> participants(String sessionId) {
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        return template.query("""
                SELECT participant_user_id,participant_role,status,state_version,added_by,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time
                FROM ai_ops_chat_session_participant
                WHERE session_id=? AND status='ACTIVE'
                ORDER BY CASE participant_role WHEN 'OWNER' THEN 0 WHEN 'EDITOR' THEN 1 ELSE 2 END, id
                """, (resultSet, rowNum) -> new ChatSessionParticipant(
                resultSet.getString("participant_user_id"),
                resultSet.getString("participant_role"),
                resultSet.getString("status"),
                resultSet.getLong("state_version"),
                resultSet.getString("added_by"),
                resultSet.getString("create_time")), sessionId);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public boolean replaceParticipants(ChatSessionParticipantReplacement replacement) {
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        int updated = template.update("""
                UPDATE ai_ops_chat_session SET state_version=state_version+1,last_active_at=CURRENT_TIMESTAMP
                WHERE session_id=? AND user_id=? AND state_version=? AND status<>'DELETED'
                """, replacement.sessionId(), replacement.ownerUserId(), replacement.expectedSessionVersion());
        if (updated != 1) return false;
        template.update("DELETE FROM ai_ops_chat_session_participant WHERE session_id=? AND participant_role<>'OWNER'",
                replacement.sessionId());
        for (ChatSessionParticipantDraft participant : replacement.participants()) {
            template.update("""
                    INSERT INTO ai_ops_chat_session_participant
                      (session_id,project_id,participant_user_id,participant_role,status,state_version,added_by)
                    VALUES (?,?,?,?, 'ACTIVE',1,?)
                    """, replacement.sessionId(), value(replacement.projectId()), participant.userId(),
                    participant.role(), replacement.ownerUserId());
        }
        return true;
    }

    @Override
    public boolean markDeleted(String sessionId) {
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        template.update("UPDATE ai_ops_chat_session SET status = 'DELETED', last_active_at = CURRENT_TIMESTAMP WHERE session_id = ?",
                sessionId);
        return true;
    }

    @Override
    public void touch(String sessionId, String title) {
        JdbcTemplate template = requiredTemplate();
        ensureSchema();
        template.update("""
                        UPDATE ai_ops_chat_session
                        SET title = CASE WHEN title IS NULL OR title = '' OR title LIKE '新会话%'
                                         THEN COALESCE(NULLIF(?, ''), title) ELSE title END,
                            last_active_at = CURRENT_TIMESTAMP,
                            state_version = state_version + 1
                        WHERE session_id = ?
                        """,
                value(title), sessionId);
    }

    private String baseSelect() {
        return """
                SELECT s.session_id, s.user_id, s.project_id, s.agent_id, s.agent_binding_mode,
                       s.agent_version, s.agent_definition_hash, s.title, s.mode, s.engine,
                       s.rag_enabled, s.knowledge_base_id, s.status, s.state_version, s.metadata,
                       DATE_FORMAT(s.create_time, '%Y-%m-%d %H:%i:%s') AS create_time,
                       DATE_FORMAT(s.last_active_at, '%Y-%m-%d %H:%i:%s') AS last_active_at,
                       (SELECT COUNT(1) FROM ai_ops_chat_message m WHERE m.session_id = s.session_id) AS message_count,
                       (SELECT m.content FROM ai_ops_chat_message m WHERE m.session_id = s.session_id ORDER BY m.id DESC LIMIT 1) AS last_message
                FROM ai_ops_chat_session s
                """;
    }

    private ChatSessionSnapshot mapSession(ResultSet resultSet, int rowNum) throws SQLException {
        return new ChatSessionSnapshot(
                resultSet.getString("session_id"),
                resultSet.getString("user_id"),
                resultSet.getString("project_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("agent_binding_mode"),
                resultSet.getObject("agent_version", Integer.class),
                resultSet.getString("agent_definition_hash"),
                resultSet.getString("title"),
                resultSet.getString("mode"),
                resultSet.getString("engine"),
                resultSet.getInt("rag_enabled") == 1,
                resultSet.getString("knowledge_base_id"),
                resultSet.getString("status"),
                resultSet.getLong("state_version"),
                metadata(resultSet.getString("metadata")),
                resultSet.getString("create_time"),
                resultSet.getString("last_active_at"),
                resultSet.getInt("message_count"),
                abbreviate(resultSet.getString("last_message"), 260));
    }

    private void ensureOwnerParticipant(JdbcTemplate template, ChatSessionSnapshot session) {
        if (!hasText(session.userId())) return;
        template.update("""
                INSERT INTO ai_ops_chat_session_participant
                  (session_id,project_id,participant_user_id,participant_role,status,state_version,added_by)
                VALUES (?,?,?,'OWNER','ACTIVE',1,?)
                ON DUPLICATE KEY UPDATE participant_role='OWNER',status='ACTIVE',project_id=VALUES(project_id)
                """, session.sessionId(), value(session.projectId()), session.userId(), session.userId());
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
        if (template == null) throw new IllegalStateException("CHAT_SESSION_STORE_UNAVAILABLE");
        return template;
    }

    private void ensureSchema() {
        if (schemaInitializer != null) schemaInitializer.initialize();
        if (conversationMemorySchemaInitializer != null) {
            conversationMemorySchemaInitializer.initialize();
        }
    }

    private boolean hasText(String input) {
        return input != null && !input.trim().isBlank();
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }

    private String abbreviate(String input, int maxLength) {
        if (!hasText(input)) return "";
        return input.length() <= maxLength ? input : input.substring(0, maxLength) + "...";
    }
}
