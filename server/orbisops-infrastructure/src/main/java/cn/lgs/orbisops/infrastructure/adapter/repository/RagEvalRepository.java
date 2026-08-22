package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagEvalRepository;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class RagEvalRepository implements IRagEvalRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.rag.eval.auto-init:true}")
    private boolean autoInit;

    private volatile boolean initialized;

    public RagEvalRepository(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public void ensureTables() {
        JdbcTemplate jdbcTemplate = template();
        if (jdbcTemplate == null || !autoInit || initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_rag_eval_case (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      case_name VARCHAR(160) NOT NULL DEFAULT '' COMMENT '用例名称',
                      query_text TEXT NOT NULL COMMENT '查询问题',
                      knowledge_tag VARCHAR(128) NOT NULL DEFAULT '' COMMENT '知识库标签',
                      expected_keywords_json TEXT NULL COMMENT '期望关键词JSON',
                      top_k INT NOT NULL DEFAULT 8 COMMENT '检索数量',
                      enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
                      create_time DATETIME NOT NULL COMMENT '创建时间',
                      update_time DATETIME NOT NULL COMMENT '更新时间',
                      PRIMARY KEY (id),
                      KEY idx_enabled (enabled),
                      KEY idx_knowledge_tag (knowledge_tag)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG离线评测用例'
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_rag_eval_run (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      case_count INT NOT NULL DEFAULT 0 COMMENT '用例数',
                      hit_rate DOUBLE NOT NULL DEFAULT 0 COMMENT '命中率',
                      average_keyword_coverage DOUBLE NOT NULL DEFAULT 0 COMMENT '平均关键词覆盖',
                      mrr DOUBLE NOT NULL DEFAULT 0 COMMENT 'MRR',
                      passed_count BIGINT NOT NULL DEFAULT 0 COMMENT '通过数',
                      result_json MEDIUMTEXT NULL COMMENT '评测结果',
                      create_time DATETIME NOT NULL COMMENT '创建时间',
                      PRIMARY KEY (id),
                      KEY idx_create_time (create_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG离线评测运行记录'
                    """);
            initialized = true;
        }
    }

    @Override
    public List<Map<String, Object>> listCases(Boolean enabled, int limit) {
        JdbcTemplate jdbcTemplate = template();
        if (jdbcTemplate == null) {
            return List.of();
        }
        ensureTables();
        int safeLimit = Math.max(1, Math.min(limit, 500));
        if (enabled == null) {
            return jdbcTemplate.queryForList("""
                    SELECT id, case_name, query_text, knowledge_tag, expected_keywords_json, top_k, enabled, create_time, update_time
                    FROM ai_rag_eval_case
                    ORDER BY id DESC
                    LIMIT ?
                    """, safeLimit);
        }
        return jdbcTemplate.queryForList("""
                SELECT id, case_name, query_text, knowledge_tag, expected_keywords_json, top_k, enabled, create_time, update_time
                FROM ai_rag_eval_case
                WHERE enabled = ?
                ORDER BY id DESC
                LIMIT ?
                """, enabled ? 1 : 0, safeLimit);
    }

    @Override
    public Long saveCase(Long id,
                         String caseName,
                         String query,
                         String knowledgeTag,
                         String expectedKeywordsJson,
                         int topK,
                         boolean enabled) {
        JdbcTemplate jdbcTemplate = template();
        if (jdbcTemplate == null) {
            return id == null ? 0L : id;
        }
        ensureTables();
        if (id == null) {
            jdbcTemplate.update("""
                    INSERT INTO ai_rag_eval_case
                      (case_name, query_text, knowledge_tag, expected_keywords_json, top_k, enabled, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """, caseName, query, knowledgeTag, expectedKeywordsJson, topK, enabled ? 1 : 0);
            return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        }
        jdbcTemplate.update("""
                UPDATE ai_rag_eval_case
                SET case_name = ?, query_text = ?, knowledge_tag = ?, expected_keywords_json = ?,
                    top_k = ?, enabled = ?, update_time = NOW()
                WHERE id = ?
                """, caseName, query, knowledgeTag, expectedKeywordsJson, topK, enabled ? 1 : 0, id);
        return id;
    }

    @Override
    public boolean deleteCase(Long id) {
        JdbcTemplate jdbcTemplate = template();
        if (jdbcTemplate == null) {
            return false;
        }
        ensureTables();
        return jdbcTemplate.update("DELETE FROM ai_rag_eval_case WHERE id = ?", id) > 0;
    }

    @Override
    public List<Map<String, Object>> listEnabledCases(int limit) {
        JdbcTemplate jdbcTemplate = template();
        if (jdbcTemplate == null) {
            return List.of();
        }
        ensureTables();
        return jdbcTemplate.queryForList("""
                SELECT id, case_name, query_text, knowledge_tag, expected_keywords_json, top_k, enabled
                FROM ai_rag_eval_case
                WHERE enabled = 1
                ORDER BY id ASC
                LIMIT ?
                """, Math.max(1, Math.min(limit, 500)));
    }

    @Override
    public void saveRun(Map<String, Object> summary) {
        JdbcTemplate jdbcTemplate = template();
        if (jdbcTemplate == null) {
            return;
        }
        ensureTables();
        jdbcTemplate.update("""
                INSERT INTO ai_rag_eval_run
                  (case_count, hit_rate, average_keyword_coverage, mrr, passed_count, result_json, create_time)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """, summary.get("caseCount"), summary.get("hitRate"), summary.get("averageKeywordCoverage"),
                summary.get("mrr"), summary.get("passedCount"), JSON.toJSONString(summary));
    }

    private JdbcTemplate template() {
        return jdbcTemplateProvider.getIfAvailable();
    }
}
