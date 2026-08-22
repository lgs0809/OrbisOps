package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.knowledge.KnowledgeWorkspaceCatalogPort;
import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeBaseCatalogRepository;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class JdbcKnowledgeBaseCatalogRepository
        implements IKnowledgeBaseCatalogRepository, KnowledgeWorkspaceCatalogPort {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.knowledge-catalog.auto-init:true}")
    private boolean autoInit;

    private volatile boolean initialized;

    public JdbcKnowledgeBaseCatalogRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public List<KnowledgeBaseCatalogEntry> list(KnowledgeScope scope, String projectId) {
        if (scope == null) {
            return List.of();
        }
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return List.of();
        }
        String project = scope == KnowledgeScope.GLOBAL ? "" : value(projectId);
        try {
            ensureTable(template);
            return template.query("""
                            SELECT id, kb_id, project_id, kb_name, scope, description, status,
                                   document_count, chunk_count, source_type, retrieval_policy_json,
                                   create_by, create_time, update_time
                            FROM ai_ops_knowledge_base
                            WHERE scope = ? AND project_id = ?
                            ORDER BY update_time DESC, id DESC
                            """,
                    (rs, rowNum) -> new KnowledgeBaseCatalogEntry(
                            rs.getLong("id"),
                            new KnowledgeBaseCatalogKey(
                                    KnowledgeScope.require(rs.getString("scope")),
                                    rs.getString("project_id"),
                                    rs.getString("kb_id")),
                            rs.getString("kb_name"),
                            rs.getString("description"),
                            KnowledgeStatus.require(rs.getString("status")),
                            rs.getLong("document_count"),
                            rs.getLong("chunk_count"),
                            rs.getString("source_type"),
                            rs.getString("retrieval_policy_json"),
                            rs.getString("create_by"),
                            timestamp(rs.getTimestamp("create_time")),
                            timestamp(rs.getTimestamp("update_time"))),
                    scope.name(), project);
        } catch (DataAccessException error) {
            log.warn("查询知识库目录失败 scope={} projectId={} reason={}", scope, project, error.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<KnowledgeBaseCatalogEntry> find(KnowledgeBaseCatalogKey key) {
        if (key == null) {
            return Optional.empty();
        }
        return list(key.scope(), key.projectId()).stream()
                .filter(entry -> key.kbId().equals(entry.key().kbId()))
                .findFirst();
    }

    @Override
    public void save(KnowledgeBaseCatalogEntry entry) {
        if (entry == null) throw new IllegalArgumentException("KNOWLEDGE_BASE_ENTRY_REQUIRED");
        if (entry.createBy().isBlank()) {
            throw new IllegalArgumentException("createBy 不能为空，知识库写操作必须绑定真实操作者");
        }
        JdbcTemplate template = requiredTemplate();
        ensureTable(template);
        template.update("""
                        INSERT INTO ai_ops_knowledge_base
                        (kb_id, project_id, kb_name, scope, description, status,
                         document_count, chunk_count, source_type, retrieval_policy_json, create_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          kb_name=VALUES(kb_name),
                          description=VALUES(description),
                          status=VALUES(status),
                          document_count=VALUES(document_count),
                          chunk_count=VALUES(chunk_count),
                          source_type=VALUES(source_type),
                          retrieval_policy_json=VALUES(retrieval_policy_json)
                        """,
                entry.key().kbId(),
                entry.key().projectId(),
                entry.name(),
                entry.key().scope().name(),
                entry.description(),
                entry.status().name(),
                entry.documentCount(),
                entry.chunkCount(),
                entry.sourceType(),
                entry.retrievalPolicyJson(),
                entry.createBy());
    }

    @Override
    public List<KnowledgeBaseCatalogEntry> listEnabledProject(String projectId) {
        return list(KnowledgeScope.PROJECT, projectId).stream()
                .filter(entry -> entry.status() == KnowledgeStatus.ENABLED)
                .toList();
    }

    @Override
    public List<KnowledgeBaseCatalogEntry> listEnabledGlobalByIds(List<String> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return List.of();
        }
        java.util.Map<String, KnowledgeBaseCatalogEntry> enabledById = new java.util.LinkedHashMap<>();
        for (KnowledgeBaseCatalogEntry entry : list(KnowledgeScope.GLOBAL, "")) {
            if (entry.status() == KnowledgeStatus.ENABLED) {
                enabledById.put(entry.key().kbId(), entry);
            }
        }
        return kbIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .map(enabledById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private JdbcTemplate requiredTemplate() {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            throw new IllegalStateException("知识库目录数据库未配置");
        }
        return template;
    }

    private JdbcTemplate availableTemplate() {
        return jdbcTemplateProvider.getIfAvailable();
    }

    private void ensureTable(JdbcTemplate template) {
        if (initialized || !autoInit) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            template.execute("""
                    CREATE TABLE IF NOT EXISTS ai_ops_knowledge_base (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      kb_id VARCHAR(128) NOT NULL COMMENT '知识库ID',
                      project_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '项目ID，GLOBAL为空',
                      kb_name VARCHAR(160) NOT NULL COMMENT '知识库名称',
                      scope VARCHAR(24) NOT NULL COMMENT 'GLOBAL/PROJECT',
                      description TEXT NULL COMMENT '说明',
                      status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
                      document_count BIGINT NOT NULL DEFAULT 0 COMMENT '文档数快照',
                      chunk_count BIGINT NOT NULL DEFAULT 0 COMMENT '结构化片段数快照',
                      source_type VARCHAR(64) NOT NULL DEFAULT 'DB' COMMENT '来源类型',
                      retrieval_policy_json MEDIUMTEXT NULL COMMENT '检索策略JSON',
                      create_by VARCHAR(128) NOT NULL DEFAULT '' COMMENT '创建人',
                      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_scope_project_kb (scope, project_id, kb_id),
                      KEY idx_project_status (project_id, status),
                      KEY idx_scope_status (scope, status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维知识库目录表'
                    """);
            initialized = true;
        }
    }

    private String timestamp(Timestamp value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
