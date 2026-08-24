package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.runtime.contextbundle.adapter.repository.IRuntimeContextBundleRepository;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcRuntimeContextBundleRepository implements IRuntimeContextBundleRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.runtime-context-bundle.auto-init:true}")
    private boolean autoInit = true;

    private volatile boolean initialized;

    public JdbcRuntimeContextBundleRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @PostConstruct
    public void init() {
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template != null) ensureTable(template);
    }

    @Override
    public RuntimeContextBundleSnapshot save(RuntimeContextBundleSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_SNAPSHOT_REQUIRED");
        JdbcTemplate template = template();
        template.update("""
                        INSERT INTO ai_ops_runtime_context_bundle
                        (bundle_id, bundle_hash, session_id, run_id, project_id, agent_id, actor,
                         memory_context_hash, memory_refs_json, used_skill_version_refs_json, used_skill_refs_hash,
                         toolset_boundary_hash, runtime_boundary_hash, bundle_json, create_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                snapshot.bundleId(),
                snapshot.bundleHash(),
                snapshot.sessionId(),
                snapshot.runId(),
                snapshot.projectId(),
                snapshot.agentId(),
                snapshot.actor(),
                snapshot.memoryContextHash(),
                JSON.toJSONString(snapshot.memoryRefs(), com.alibaba.fastjson.serializer.SerializerFeature.DisableCircularReferenceDetect),
                JSON.toJSONString(snapshot.usedSkillVersionRefs(), com.alibaba.fastjson.serializer.SerializerFeature.DisableCircularReferenceDetect),
                snapshot.usedSkillRefsHash(),
                snapshot.toolsetBoundaryHash(),
                snapshot.runtimeBoundaryHash(),
                JSON.toJSONString(snapshot.compatiblePayload(), com.alibaba.fastjson.serializer.SerializerFeature.DisableCircularReferenceDetect),
                Timestamp.from(snapshot.createdAt()));
        return snapshot;
    }

    @Override
    public Optional<RuntimeContextBundleSnapshot> find(String bundleId) {
        String id = required(bundleId, "contextBundleId 不能为空");
        return query("""
                SELECT * FROM ai_ops_runtime_context_bundle
                WHERE bundle_id=? LIMIT 1
                """, id);
    }

    @Override
    public Optional<RuntimeContextBundleSnapshot> latestForSession(String sessionId, String projectId) {
        return query("""
                SELECT * FROM ai_ops_runtime_context_bundle
                WHERE session_id=? AND project_id=?
                ORDER BY create_time DESC, id DESC LIMIT 1
                """,
                required(sessionId, "sessionId 不能为空"),
                required(projectId, "projectId 不能为空"));
    }

    @Override
    public Optional<RuntimeContextBundleSnapshot> latestCompletedForSession(
            String sessionId,
            String projectId,
            String actor) {
        return query("""
                SELECT b.*
                FROM ai_ops_runtime_context_bundle b
                INNER JOIN ai_ops_agent_run r
                  ON r.run_id=b.run_id AND r.project_id=b.project_id AND r.session_id=b.session_id
                WHERE b.session_id=? AND b.project_id=? AND r.user_id=? AND r.status='SUCCEEDED'
                ORDER BY b.create_time DESC, b.id DESC LIMIT 1
                """,
                required(sessionId, "sessionId 不能为空"),
                required(projectId, "projectId 不能为空"),
                required(actor, "actor 不能为空"));
    }

    private Optional<RuntimeContextBundleSnapshot> query(String sql, Object... args) {
        return template().query(sql, this::snapshot, args).stream().findFirst();
    }

    private RuntimeContextBundleSnapshot snapshot(ResultSet resultSet, int rowNum) throws SQLException {
        String bundleId = resultSet.getString("bundle_id");
        String bundleHash = resultSet.getString("bundle_hash");
        String memoryHash = text(resultSet.getString("memory_context_hash"));
        List<Map<String, Object>> memoryRefs = mapList(resultSet.getString("memory_refs_json"));
        List<Map<String, Object>> skillRefs = mapList(resultSet.getString("used_skill_version_refs_json"));
        String skillHash = text(resultSet.getString("used_skill_refs_hash"));
        String toolsetHash = text(resultSet.getString("toolset_boundary_hash"));
        String runtimeHash = text(resultSet.getString("runtime_boundary_hash"));
        Map<String, Object> payload = objectMap(resultSet.getString("bundle_json"));
        payload.put("contextBundleId", bundleId);
        payload.put("contextBundleHash", bundleHash);
        payload.putIfAbsent("memoryContextRefs", memoryRefs);
        payload.putIfAbsent("memoryContextHash", memoryHash);
        // Older JSON may encode shared immutable lists as $ref objects. The dedicated
        // columns retain the same frozen references; never substitute a live selection.
        Object selected = payload.get("usedSkillVersionRefs");
        if (selected instanceof Map<?, ?> ref && ref.size() == 1 && ref.containsKey("$ref")) {
            payload.put("usedSkillVersionRefs", skillRefs);
        } else {
            payload.putIfAbsent("usedSkillVersionRefs", skillRefs);
        }
        payload.putIfAbsent("usedSkillRefsHash", skillHash);
        payload.putIfAbsent("toolsetBoundaryHash", toolsetHash);
        payload.putIfAbsent("runtimeBoundaryHash", runtimeHash);
        Timestamp created = resultSet.getTimestamp("create_time");
        return new RuntimeContextBundleSnapshot(
                resultSet.getLong("id"),
                bundleId,
                bundleHash,
                resultSet.getString("session_id"),
                resultSet.getString("run_id"),
                resultSet.getString("project_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("actor"),
                memoryHash,
                memoryRefs,
                skillRefs,
                skillHash,
                toolsetHash,
                runtimeHash,
                payload,
                created == null ? Instant.EPOCH : created.toInstant());
    }

    private Map<String, Object> objectMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            Map<String, Object> value = JSON.parseObject(json,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
        } catch (RuntimeException error) {
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> mapList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<LinkedHashMap<String, Object>> values = JSON.parseObject(json,
                    new TypeReference<List<LinkedHashMap<String, Object>>>() {
                    });
            if (values == null) return List.of();
            return values.stream().map(value -> (Map<String, Object>) value).toList();
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    private JdbcTemplate template() {
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) throw new IllegalStateException("Runtime Context Bundle 持久化未初始化");
        ensureTable(template);
        return template;
    }

    private void ensureTable(JdbcTemplate template) {
        if (initialized || !autoInit) return;
        synchronized (this) {
            if (initialized) return;
            try {
                template.execute("""
                        CREATE TABLE IF NOT EXISTS ai_ops_runtime_context_bundle (
                          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                          bundle_id VARCHAR(80) NOT NULL,
                          bundle_hash VARCHAR(128) NOT NULL,
                          session_id VARCHAR(80) NOT NULL DEFAULT '',
                          run_id VARCHAR(80) NOT NULL DEFAULT '',
                          project_id VARCHAR(128) NOT NULL DEFAULT '',
                          agent_id VARCHAR(128) NOT NULL DEFAULT '',
                          actor VARCHAR(128) NOT NULL DEFAULT '',
                          memory_context_hash VARCHAR(128) NOT NULL DEFAULT '',
                          memory_refs_json MEDIUMTEXT NULL,
                          used_skill_version_refs_json MEDIUMTEXT NULL,
                          used_skill_refs_hash VARCHAR(128) NOT NULL DEFAULT '',
                          toolset_boundary_hash VARCHAR(128) NOT NULL DEFAULT '',
                          runtime_boundary_hash VARCHAR(128) NOT NULL DEFAULT '',
                          bundle_json MEDIUMTEXT NOT NULL,
                          create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_bundle_id (bundle_id),
                          KEY idx_session_time (session_id, create_time),
                          KEY idx_project_time (project_id, create_time)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维运行时上下文Bundle表'
                        """);
                initialized = true;
            } catch (RuntimeException error) {
                throw new IllegalStateException(
                        "初始化 Runtime Context Bundle 表失败，安全主链路必须 fail closed：" + error.getMessage(),
                        error);
            }
        }
    }

    private String required(String value, String message) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
