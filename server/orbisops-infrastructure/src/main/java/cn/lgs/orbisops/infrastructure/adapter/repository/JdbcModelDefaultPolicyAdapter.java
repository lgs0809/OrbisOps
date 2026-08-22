package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicyPort;
import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicySnapshot;
import cn.lgs.orbisops.domain.modelpolicy.model.ModelDefaultPolicy;
import cn.lgs.orbisops.domain.modelpolicy.model.ModelPolicyStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** JDBC anti-corruption adapter for typed default-model policy facts. */
@Repository
public class JdbcModelDefaultPolicyAdapter implements ModelDefaultPolicyPort {

    private final JdbcTemplate jdbcTemplate;
    private final boolean autoInit;
    private volatile boolean initialized;

    public JdbcModelDefaultPolicyAdapter(
            @Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Value("${orbisops.model-default-policy.auto-init:true}") boolean autoInit) {
        this.jdbcTemplate = jdbcTemplate;
        this.autoInit = autoInit;
    }

    @PostConstruct
    public void init() {
        ensureTable();
    }

    @Override
    public Optional<ModelDefaultPolicySnapshot> find(String projectId) {
        ensureTable();
        String scopeProjectId = normalizeProjectId(projectId);
        List<ModelDefaultPolicySnapshot> rows = jdbcTemplate.query("""
                        SELECT id, project_id, default_chat_model_id, default_embedding_model_id,
                               default_rerank_model_id, default_vision_model_id, status, create_time, update_time
                        FROM ai_ops_model_default_policy
                        WHERE project_id = ?
                        LIMIT 1
                        """,
                (rs, rowNum) -> new ModelDefaultPolicySnapshot(
                        rs.getLong("id"),
                        new ModelDefaultPolicy(
                                rs.getString("project_id"),
                                rs.getString("default_chat_model_id"),
                                rs.getString("default_embedding_model_id"),
                                rs.getString("default_rerank_model_id"),
                                rs.getString("default_vision_model_id"),
                                ModelPolicyStatus.require(rs.getString("status"))),
                        localDateTime(rs.getTimestamp("create_time")),
                        localDateTime(rs.getTimestamp("update_time"))),
                scopeProjectId);
        return rows.stream().findFirst();
    }

    @Override
    public ModelDefaultPolicySnapshot save(ModelDefaultPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("MODEL_DEFAULT_POLICY_REQUIRED");
        ensureTable();
        jdbcTemplate.update("""
                        INSERT INTO ai_ops_model_default_policy
                        (project_id, default_chat_model_id, default_embedding_model_id,
                         default_rerank_model_id, default_vision_model_id, status)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          default_chat_model_id=VALUES(default_chat_model_id),
                          default_embedding_model_id=VALUES(default_embedding_model_id),
                          default_rerank_model_id=VALUES(default_rerank_model_id),
                          default_vision_model_id=VALUES(default_vision_model_id),
                          status=VALUES(status)
                        """,
                normalizeProjectId(policy.projectId()),
                policy.chatModelId(),
                policy.embeddingModelId(),
                policy.rerankModelId(),
                policy.visionModelId(),
                policy.status().name());
        return find(policy.projectId())
                .orElseThrow(() -> new IllegalStateException("MODEL_DEFAULT_POLICY_SAVE_NOT_VISIBLE"));
    }

    private LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String normalizeProjectId(String projectId) {
        return projectId == null ? "" : projectId.trim();
    }

    private void ensureTable() {
        if (initialized || !autoInit) return;
        synchronized (this) {
            if (initialized) return;
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_ops_model_default_policy (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      project_id VARCHAR(80) NOT NULL DEFAULT '' COMMENT '项目ID，空表示全局默认',
                      default_chat_model_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '默认Chat模型',
                      default_embedding_model_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '默认Embedding模型',
                      default_rerank_model_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '默认Rerank模型',
                      default_vision_model_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '默认Vision模型',
                      status VARCHAR(24) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
                      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_model_policy_project (project_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='默认模型策略'
                    """);
            initialized = true;
        }
    }
}
