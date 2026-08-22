package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.adapter.repository.IProjectKnowledgeAuthorizationRepository;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeAuthorizationUsageCount;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
import cn.lgs.orbisops.domain.knowledge.model.ProjectKnowledgeAuthorization;
import cn.lgs.orbisops.domain.knowledge.model.ProjectKnowledgeAuthorizationUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Slf4j
@Repository
public class JdbcProjectKnowledgeAuthorizationRepository
        implements IProjectKnowledgeAuthorizationRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.knowledge-catalog.auto-init:true}")
    private boolean autoInit;

    private volatile boolean initialized;

    public JdbcProjectKnowledgeAuthorizationRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public ProjectKnowledgeAuthorization save(ProjectKnowledgeAuthorization authorization) {
        if (authorization == null) {
            throw new IllegalArgumentException("KNOWLEDGE_AUTHORIZATION_REQUIRED");
        }
        JdbcTemplate template = requiredTemplate();
        ensureTable(template);
        template.update("""
                        INSERT INTO ai_ops_project_knowledge_base
                        (project_id, global_kb_id, status, enabled_by)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          status=VALUES(status),
                          enabled_by=VALUES(enabled_by),
                          enabled_time=CURRENT_TIMESTAMP
                        """,
                authorization.projectId(),
                authorization.globalKbId(),
                authorization.status().name(),
                authorization.enabledBy());
        return authorization;
    }

    @Override
    public List<String> listEnabledKnowledgeBaseIds(String projectId) {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return List.of();
        }
        try {
            ensureTable(template);
            return template.queryForList("""
                            SELECT global_kb_id
                            FROM ai_ops_project_knowledge_base
                            WHERE project_id = ? AND status = ?
                            ORDER BY id ASC
                            """,
                    String.class,
                    projectId,
                    KnowledgeStatus.ENABLED.name());
        } catch (DataAccessException error) {
            log.warn("查询项目启用通用知识库失败 projectId={} reason={}", projectId, error.getMessage());
            return List.of();
        }
    }

    @Override
    public List<ProjectKnowledgeAuthorizationUsage> listUsageProjects(String globalKbId) {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return List.of();
        }
        try {
            ensureTable(template);
            return template.query("""
                            SELECT binding.project_id,
                                   project.name AS project_name,
                                   binding.global_kb_id,
                                   binding.status,
                                   binding.enabled_by,
                                   binding.enabled_time,
                                   binding.update_time
                            FROM ai_ops_project_knowledge_base binding
                            LEFT JOIN ai_ops_project project
                              ON project.project_id = binding.project_id
                            WHERE binding.global_kb_id = ?
                            ORDER BY binding.update_time DESC, binding.id DESC
                            """,
                    (rs, rowNum) -> new ProjectKnowledgeAuthorizationUsage(
                            rs.getString("project_id"),
                            rs.getString("project_name"),
                            rs.getString("global_kb_id"),
                            KnowledgeStatus.require(rs.getString("status")),
                            rs.getString("enabled_by"),
                            timestamp(rs.getTimestamp("enabled_time")),
                            timestamp(rs.getTimestamp("update_time"))),
                    globalKbId);
        } catch (DataAccessException error) {
            log.warn("查询通用知识库使用项目失败 kbId={} reason={}", globalKbId, error.getMessage());
            return List.of();
        }
    }

    @Override
    public List<KnowledgeAuthorizationUsageCount> listEnabledUsageCounts() {
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return List.of();
        }
        try {
            ensureTable(template);
            return template.query("""
                            SELECT global_kb_id, COUNT(DISTINCT project_id) AS project_count
                            FROM ai_ops_project_knowledge_base
                            WHERE status = ?
                            GROUP BY global_kb_id
                            """,
                    (rs, rowNum) -> new KnowledgeAuthorizationUsageCount(
                            rs.getString("global_kb_id"),
                            rs.getLong("project_count")),
                    KnowledgeStatus.ENABLED.name());
        } catch (DataAccessException error) {
            log.warn("统计通用知识库使用项目失败 reason={}", error.getMessage());
            return List.of();
        }
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
                    CREATE TABLE IF NOT EXISTS ai_ops_project_knowledge_base (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      project_id VARCHAR(128) NOT NULL COMMENT '项目ID',
                      global_kb_id VARCHAR(128) NOT NULL COMMENT '启用的通用知识库ID',
                      status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
                      enabled_by VARCHAR(128) NOT NULL DEFAULT '' COMMENT '启用人',
                      enabled_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '启用时间',
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_project_global_kb (project_id, global_kb_id),
                      KEY idx_project_status (project_id, status),
                      KEY idx_global_kb (global_kb_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目启用通用知识库关系表'
                    """);
            initialized = true;
        }
    }

    private String timestamp(Timestamp value) {
        return value == null ? "" : String.valueOf(value);
    }
}
