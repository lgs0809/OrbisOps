package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeRetrievalPolicyRepository;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicy;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicyKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicyState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class JdbcKnowledgeRetrievalPolicyRepository implements IKnowledgeRetrievalPolicyRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.knowledge-catalog.auto-init:true}")
    private boolean autoInit;

    private volatile boolean initialized;

    public JdbcKnowledgeRetrievalPolicyRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public Optional<KnowledgeRetrievalPolicyState> find(KnowledgeRetrievalPolicyKey key) {
        if (key == null) {
            return Optional.empty();
        }
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) {
            return Optional.empty();
        }
        try {
            ensureTable(template);
            List<KnowledgeRetrievalPolicyState> states = template.query("""
                            SELECT id, chunk_size, overlap_size, top_k, rerank_enabled,
                                   embedding_model_id, metadata_filter_json, update_time
                            FROM ai_ops_knowledge_retrieval_policy
                            WHERE kb_scope = ? AND project_id = ? AND kb_id = ?
                            LIMIT 1
                            """,
                    (rs, rowNum) -> new KnowledgeRetrievalPolicyState(
                            rs.getLong("id"),
                            key,
                            new KnowledgeRetrievalPolicy(
                                    rs.getInt("chunk_size"),
                                    rs.getInt("overlap_size"),
                                    rs.getInt("top_k"),
                                    rs.getBoolean("rerank_enabled"),
                                    rs.getString("embedding_model_id"),
                                    rs.getString("metadata_filter_json")),
                            String.valueOf(rs.getTimestamp("update_time"))),
                    key.scope().name(), key.projectId(), key.kbId());
            return states.stream().findFirst();
        } catch (DataAccessException error) {
            log.warn("查询知识库检索策略失败 scope={} projectId={} kbId={} reason={}",
                    key.scope(), key.projectId(), key.kbId(), error.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public KnowledgeRetrievalPolicyState save(KnowledgeRetrievalPolicyKey key,
                                              KnowledgeRetrievalPolicy policy) {
        if (key == null) throw new IllegalArgumentException("KNOWLEDGE_RETRIEVAL_POLICY_KEY_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("KNOWLEDGE_RETRIEVAL_POLICY_REQUIRED");
        JdbcTemplate template = jdbcTemplateProvider.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("知识库目录数据库未配置");
        }
        ensureTable(template);
        template.update("""
                        INSERT INTO ai_ops_knowledge_retrieval_policy
                        (kb_id, kb_scope, project_id, chunk_size, overlap_size, top_k,
                         rerank_enabled, embedding_model_id, metadata_filter_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          chunk_size=VALUES(chunk_size),
                          overlap_size=VALUES(overlap_size),
                          top_k=VALUES(top_k),
                          rerank_enabled=VALUES(rerank_enabled),
                          embedding_model_id=VALUES(embedding_model_id),
                          metadata_filter_json=VALUES(metadata_filter_json)
                        """,
                key.kbId(),
                key.scope().name(),
                key.projectId(),
                policy.maxSegmentChars(),
                policy.hardSplitOverlapChars(),
                policy.topK(),
                policy.rerankEnabled(),
                policy.embeddingModelId(),
                policy.metadataFilterJson());
        return find(key).orElseGet(() -> KnowledgeRetrievalPolicyState.transientState(key, policy));
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
                    CREATE TABLE IF NOT EXISTS ai_ops_knowledge_retrieval_policy (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      kb_id VARCHAR(128) NOT NULL COMMENT '知识库ID',
                      kb_scope VARCHAR(24) NOT NULL COMMENT 'GLOBAL/PROJECT',
                      project_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '项目ID，GLOBAL为空',
                      chunk_size INT NOT NULL DEFAULT 3000 COMMENT '结构单元超长时的最大分段字符数',
                      overlap_size INT NOT NULL DEFAULT 0 COMMENT '仅用于不可再按结构拆分的超长块重叠字符数',
                      top_k INT NOT NULL DEFAULT 5 COMMENT '召回数量',
                      rerank_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用 rerank',
                      embedding_model_id VARCHAR(160) NOT NULL DEFAULT '' COMMENT 'Embedding 模型',
                      metadata_filter_json MEDIUMTEXT NULL COMMENT 'metadata 过滤条件',
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_scope_project_kb_policy (kb_scope, project_id, kb_id),
                      KEY idx_project_scope (project_id, kb_scope)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维知识库解析与检索策略表'
                    """);
            initialized = true;
        }
    }
}
