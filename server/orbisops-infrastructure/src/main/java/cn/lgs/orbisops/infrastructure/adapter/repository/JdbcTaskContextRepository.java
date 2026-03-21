package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.runtime.taskcontext.adapter.repository.ITaskContextRepository;
import cn.lgs.orbisops.domain.runtime.taskcontext.exception.TaskContextVersionConflictException;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextContent;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextSnapshot;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextState;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
public class JdbcTaskContextRepository implements ITaskContextRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.task-context.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean initialized;

    public JdbcTaskContextRepository(
            @Qualifier("mysqlJdbcTemplate")
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @PostConstruct
    public void init() {
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template != null) ensureTable(template);
    }

    @Override
    public Optional<TaskContextSnapshot> find(String runId) {
        String normalized = required(runId);
        JdbcTemplate template = requireTemplate();
        try {
            ensureTable(template);
            return query(template, normalized);
        } catch (DataAccessException error) {
            throw new IllegalStateException("Task Context 数据库查询失败", error);
        }
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public Optional<TaskContextSnapshot> trySave(
            TaskContextSnapshot snapshot,
            int expectedVersion) {
        if (snapshot == null) throw new IllegalArgumentException("TASK_CONTEXT_SNAPSHOT_REQUIRED");
        if (expectedVersion < 0) throw new IllegalArgumentException("TASK_CONTEXT_EXPECTED_VERSION_INVALID");
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) return Optional.empty();
        try {
            ensureTable(template);
            if (expectedVersion == 0) insert(template, snapshot);
            else update(template, snapshot, expectedVersion);
            return Optional.of(snapshot);
        } catch (TaskContextVersionConflictException conflict) {
            throw conflict;
        } catch (DuplicateKeyException conflict) {
            throw new TaskContextVersionConflictException(snapshot.runId(), expectedVersion);
        } catch (DataAccessException error) {
            log.debug("写入 Task Context 失败 runId={}：{}", snapshot.runId(), error.getMessage());
            return Optional.empty();
        }
    }

    private void insert(JdbcTemplate template, TaskContextSnapshot snapshot) {
        template.update("""
                        INSERT INTO ai_ops_task_context
                          (run_id, session_id, project_id, agent_id, task_state, context_json,
                           summary, last_event_id, version, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                        """,
                snapshot.runId(),
                snapshot.sessionId(),
                snapshot.projectId(),
                snapshot.agentId(),
                snapshot.state().name(),
                JSON.toJSONString(contentMap(snapshot.content())),
                snapshot.summary(),
                snapshot.lastEventId(),
                Timestamp.valueOf(snapshot.createdAt()),
                Timestamp.valueOf(snapshot.updatedAt()));
    }

    private void update(
            JdbcTemplate template,
            TaskContextSnapshot snapshot,
            int expectedVersion) {
        int updated = template.update("""
                        UPDATE ai_ops_task_context
                        SET session_id = ?, project_id = ?, agent_id = ?, task_state = ?,
                            context_json = ?, summary = ?, last_event_id = ?,
                            version = version + 1, update_time = ?
                        WHERE run_id = ? AND version = ?
                        """,
                snapshot.sessionId(),
                snapshot.projectId(),
                snapshot.agentId(),
                snapshot.state().name(),
                JSON.toJSONString(contentMap(snapshot.content())),
                snapshot.summary(),
                snapshot.lastEventId(),
                Timestamp.valueOf(snapshot.updatedAt()),
                snapshot.runId(),
                expectedVersion);
        if (updated != 1) {
            throw new TaskContextVersionConflictException(snapshot.runId(), expectedVersion);
        }
    }

    private Optional<TaskContextSnapshot> query(JdbcTemplate template, String runId) {
        return template.query("""
                        SELECT id, run_id, session_id, project_id, agent_id, task_state,
                               context_json, summary, last_event_id, version, create_time, update_time
                        FROM ai_ops_task_context
                        WHERE run_id = ?
                        LIMIT 1
                        """,
                this::snapshot,
                runId).stream().findFirst();
    }

    private TaskContextSnapshot snapshot(ResultSet resultSet, int rowNum) throws SQLException {
        return new TaskContextSnapshot(
                resultSet.getLong("id"),
                resultSet.getString("run_id"),
                resultSet.getString("session_id"),
                resultSet.getString("project_id"),
                resultSet.getString("agent_id"),
                TaskContextState.progress(resultSet.getString("task_state")),
                content(resultSet.getString("context_json")),
                resultSet.getString("summary"),
                nullableLong(resultSet, "last_event_id"),
                resultSet.getInt("version"),
                time(resultSet.getTimestamp("create_time")),
                time(resultSet.getTimestamp("update_time")));
    }

    private Map<String, Object> contentMap(TaskContextContent content) {
        TaskContextContent safe = content == null ? TaskContextContent.empty() : content;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("goal", safe.goal());
        result.put("temporaryConstraints", safe.temporaryConstraints());
        result.put("knownFacts", safe.knownFacts());
        result.put("ruledOut", safe.ruledOut());
        result.put("openQuestions", safe.openQuestions());
        result.put("completedActions", safe.completedActions());
        result.put("pendingActions", safe.pendingActions());
        result.put("lastToolResultsSummary", safe.lastToolResultsSummary());
        result.put("usedSkills", safe.usedSkills());
        return result;
    }

    private TaskContextContent content(String json) {
        if (json == null || json.isBlank()) return TaskContextContent.empty();
        try {
            JSONObject object = JSON.parseObject(json);
            if (object == null) return TaskContextContent.empty();
            return new TaskContextContent(
                    text(object.get("goal")),
                    strings(object.get("temporaryConstraints")),
                    strings(object.get("knownFacts")),
                    strings(object.get("ruledOut")),
                    strings(object.get("openQuestions")),
                    strings(object.get("completedActions")),
                    strings(object.get("pendingActions")),
                    strings(object.get("lastToolResultsSummary")),
                    strings(object.get("usedSkills")));
        } catch (RuntimeException ignored) {
            return TaskContextContent.empty();
        }
    }

    private List<String> strings(Object value) {
        if (value instanceof JSONArray array) {
            List<String> result = new ArrayList<>();
            for (Object item : array) result.add(text(item));
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            iterable.forEach(item -> result.add(text(item)));
            return result;
        }
        return List.of();
    }

    private JdbcTemplate requireTemplate() {
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) throw new IllegalStateException("Task Context 数据库未配置");
        return template;
    }

    private void ensureTable(JdbcTemplate template) {
        if (initialized || !autoInit) return;
        synchronized (this) {
            if (initialized) return;
            template.execute("""
                    CREATE TABLE IF NOT EXISTS ai_ops_task_context (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                      run_id VARCHAR(80) NOT NULL,
                      session_id VARCHAR(80) DEFAULT '',
                      project_id VARCHAR(128) DEFAULT '',
                      agent_id VARCHAR(128) DEFAULT '',
                      task_state VARCHAR(32) DEFAULT '',
                      context_json MEDIUMTEXT NOT NULL,
                      summary TEXT,
                      last_event_id BIGINT DEFAULT NULL,
                      version INT NOT NULL DEFAULT 1,
                      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_run_id (run_id),
                      KEY idx_session_id (session_id),
                      KEY idx_project_id (project_id),
                      KEY idx_update_time (update_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维Agent当前任务上下文'
                    """);
            initialized = true;
        }
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private LocalDateTime time(Timestamp timestamp) {
        return timestamp == null ? LocalDateTime.now() : timestamp.toLocalDateTime();
    }

    private String required(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("runId 不能为空");
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
