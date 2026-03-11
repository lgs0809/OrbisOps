package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagFeedbackRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Repository
public class RagFeedbackRepository implements IRagFeedbackRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public RagFeedbackRepository(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public void ensureTables() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        if (jdbcTemplate == null) {
            return;
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_rag_feedback (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  query_text TEXT NOT NULL COMMENT '查询问题',
                  answer_text MEDIUMTEXT NULL COMMENT '回答内容',
                  useful TINYINT NULL COMMENT '是否有用',
                  resolved TINYINT NULL COMMENT '是否解决问题',
                  source_type VARCHAR(64) NULL COMMENT '来源类型',
                  source_id VARCHAR(128) NULL COMMENT '来源ID',
                  knowledge_tag VARCHAR(128) NULL COMMENT '知识库标签',
                  chunk_ids_json TEXT NULL COMMENT '命中chunk ID',
                  comment_text TEXT NULL COMMENT '反馈说明',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  PRIMARY KEY (id),
                  KEY idx_tag_time (knowledge_tag, create_time),
                  KEY idx_useful_resolved (useful, resolved)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 检索反馈表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_rag_knowledge_gap (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  gap_key VARCHAR(64) NOT NULL COMMENT '缺口去重键',
                  query_text TEXT NOT NULL COMMENT '样例问题',
                  knowledge_tag VARCHAR(128) NULL COMMENT '知识库标签',
                  status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/TRIAGED/FIXED/IGNORED',
                  feedback_count INT NOT NULL DEFAULT 1 COMMENT '反馈次数',
                  last_feedback_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近反馈时间',
                  sample_comment TEXT NULL COMMENT '样例说明',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_gap_key (gap_key),
                  KEY idx_status_update (status, update_time),
                  KEY idx_tag_status (knowledge_tag, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 知识缺口表'
                """);
    }

    @Override
    public Long insertFeedback(String query,
                               String answer,
                               Boolean useful,
                               Boolean resolved,
                               String sourceType,
                               String sourceId,
                               String knowledgeTag,
                               String chunkIdsJson,
                               String comment) {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        if (jdbcTemplate == null) {
            return 0L;
        }
        jdbcTemplate.update("""
                        INSERT INTO ai_rag_feedback
                        (query_text, answer_text, useful, resolved, source_type, source_id, knowledge_tag, chunk_ids_json, comment_text)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                query,
                answer,
                useful == null ? null : useful ? 1 : 0,
                resolved == null ? null : resolved ? 1 : 0,
                sourceType,
                sourceId,
                knowledgeTag,
                chunkIdsJson,
                comment);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Override
    public Map<String, Object> upsertGap(String query, String knowledgeTag, String comment) {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        if (jdbcTemplate == null) {
            return Map.of();
        }
        String gapKey = DigestUtils.md5DigestAsHex((query + "::" + knowledgeTag).getBytes(StandardCharsets.UTF_8));
        jdbcTemplate.update("""
                        INSERT INTO ai_rag_knowledge_gap
                          (gap_key, query_text, knowledge_tag, status, feedback_count, sample_comment)
                        VALUES (?, ?, ?, 'OPEN', 1, ?)
                        ON DUPLICATE KEY UPDATE
                          feedback_count = feedback_count + 1,
                          last_feedback_at = CURRENT_TIMESTAMP,
                          sample_comment = COALESCE(NULLIF(VALUES(sample_comment), ''), sample_comment),
                          update_time = CURRENT_TIMESTAMP
                        """,
                gapKey, query, knowledgeTag, comment);
        return jdbcTemplate.queryForMap("""
                SELECT id, gap_key AS gapKey, query_text AS queryText, knowledge_tag AS knowledgeTag,
                       status, feedback_count AS feedbackCount, sample_comment AS sampleComment,
                       DATE_FORMAT(last_feedback_at, '%Y-%m-%d %H:%i:%s') AS lastFeedbackAt
                FROM ai_rag_knowledge_gap
                WHERE gap_key = ?
                """, gapKey);
    }

    @Override
    public List<Map<String, Object>> listFeedback(String knowledgeTag, Boolean useful, Boolean resolved, int limit) {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        if (jdbcTemplate == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 500));
        Integer usefulValue = useful == null ? null : useful ? 1 : 0;
        Integer resolvedValue = resolved == null ? null : resolved ? 1 : 0;
        if (StringUtils.hasText(knowledgeTag)) {
            return jdbcTemplate.queryForList("""
                    SELECT id, query_text AS queryText, answer_text AS answerText, useful, resolved,
                           source_type AS sourceType, source_id AS sourceId, knowledge_tag AS knowledgeTag,
                           chunk_ids_json AS chunkIdsJson, comment_text AS commentText,
                           DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
                    FROM ai_rag_feedback
                    WHERE knowledge_tag = ?
                      AND (? IS NULL OR useful = ?)
                      AND (? IS NULL OR resolved = ?)
                    ORDER BY id DESC
                    LIMIT ?
                    """, knowledgeTag, usefulValue, usefulValue, resolvedValue, resolvedValue, safeLimit);
        }
        return jdbcTemplate.queryForList("""
                SELECT id, query_text AS queryText, answer_text AS answerText, useful, resolved,
                       source_type AS sourceType, source_id AS sourceId, knowledge_tag AS knowledgeTag,
                       chunk_ids_json AS chunkIdsJson, comment_text AS commentText,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime
                FROM ai_rag_feedback
                WHERE (? IS NULL OR useful = ?)
                  AND (? IS NULL OR resolved = ?)
                ORDER BY id DESC
                LIMIT ?
                """, usefulValue, usefulValue, resolvedValue, resolvedValue, safeLimit);
    }

    @Override
    public List<Map<String, Object>> listGaps(String status, String knowledgeTag, int limit) {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        if (jdbcTemplate == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                SELECT id, gap_key AS gapKey, query_text AS queryText, knowledge_tag AS knowledgeTag,
                       status, feedback_count AS feedbackCount, sample_comment AS sampleComment,
                       DATE_FORMAT(last_feedback_at, '%Y-%m-%d %H:%i:%s') AS lastFeedbackAt,
                       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime,
                       DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS updateTime
                FROM ai_rag_knowledge_gap
                WHERE (? IS NULL OR status = ?)
                  AND (? IS NULL OR knowledge_tag = ?)
                ORDER BY FIELD(status, 'OPEN', 'TRIAGED', 'FIXED', 'IGNORED'), update_time DESC, id DESC
                LIMIT ?
                """,
                emptyToNull(status), emptyToNull(status), emptyToNull(knowledgeTag), emptyToNull(knowledgeTag), Math.max(1, Math.min(limit, 500)));
    }

    @Override
    public boolean updateGapStatus(Long id, String status) {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        if (jdbcTemplate == null) {
            return false;
        }
        return jdbcTemplate.update("""
                UPDATE ai_rag_knowledge_gap
                SET status = ?, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, status, id) > 0;
    }

    @Override
    public Map<String, Object> queryGap(Long id) {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        if (jdbcTemplate == null) {
            return Map.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, query_text AS queryText, knowledge_tag AS knowledgeTag, feedback_count AS feedbackCount
                FROM ai_rag_knowledge_gap
                WHERE id = ?
                LIMIT 1
                """, id);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private JdbcTemplate jdbcTemplate() {
        return jdbcTemplateProvider.getIfAvailable();
    }
}
