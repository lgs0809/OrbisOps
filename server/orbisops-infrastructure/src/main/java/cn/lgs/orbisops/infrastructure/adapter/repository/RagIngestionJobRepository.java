package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagIngestionJobRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagIngestionJob;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagIngestionJobStatus;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Repository
public class RagIngestionJobRepository implements IRagIngestionJobRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.rag.ingestion.jdbc-enabled:true}")
    private boolean jdbcEnabled;

    @Value("${orbisops.rag.ingestion.auto-init:true}")
    private boolean autoInit;

    private volatile boolean initialized;
    private volatile boolean unavailableLogged;

    public RagIngestionJobRepository(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public void save(RagIngestionJob job) {
        JdbcTemplate template = jdbcTemplate();
        if (template == null) {
            return;
        }
        try {
            ensureTable(template);
            template.update("""
                            INSERT INTO ai_rag_ingestion_job
                            (job_id, status, name, tag, file_names_json, total_bytes, error_message, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                              status=VALUES(status), file_names_json=VALUES(file_names_json), total_bytes=VALUES(total_bytes),
                              error_message=VALUES(error_message), updated_at=VALUES(updated_at)
                            """,
                    job.jobId(),
                    job.status().name(),
                    job.name(),
                    job.tag(),
                    JSON.toJSONString(job.fileNames()),
                    job.totalBytes(),
                    job.errorMessage(),
                    job.createdAt(),
                    job.updatedAt());
        } catch (DataAccessException e) {
            logFallback(e);
        }
    }

    @Override
    public RagIngestionJob get(String jobId) {
        JdbcTemplate template = jdbcTemplate();
        if (template == null || !StringUtils.hasText(jobId)) {
            return null;
        }
        try {
            ensureTable(template);
            List<RagIngestionJob> records = template.query("""
                            SELECT job_id, status, name, tag, file_names_json, total_bytes, error_message, created_at, updated_at
                            FROM ai_rag_ingestion_job
                            WHERE job_id = ?
                            """,
                    (rs, rowNum) -> toRecord(rs.getString("job_id"),
                            rs.getString("status"),
                            rs.getString("name"),
                            rs.getString("tag"),
                            rs.getString("file_names_json"),
                            longNumber(rs.getObject("total_bytes")),
                            rs.getString("error_message"),
                            rs.getString("created_at"),
                            rs.getString("updated_at")),
                    jobId);
            return records.isEmpty() ? null : records.get(0);
        } catch (DataAccessException e) {
            logFallback(e);
            return null;
        }
    }

    @Override
    public List<RagIngestionJob> list(int limit) {
        JdbcTemplate template = jdbcTemplate();
        if (template == null) {
            return List.of();
        }
        try {
            ensureTable(template);
            return template.query("""
                            SELECT job_id, status, name, tag, file_names_json, total_bytes, error_message, created_at, updated_at
                            FROM ai_rag_ingestion_job
                            ORDER BY id DESC
                            LIMIT ?
                            """,
                    (rs, rowNum) -> toRecord(rs.getString("job_id"),
                            rs.getString("status"),
                            rs.getString("name"),
                            rs.getString("tag"),
                            rs.getString("file_names_json"),
                            longNumber(rs.getObject("total_bytes")),
                            rs.getString("error_message"),
                            rs.getString("created_at"),
                            rs.getString("updated_at")),
                    Math.max(1, Math.min(limit, 100)));
        } catch (DataAccessException e) {
            logFallback(e);
            return List.of();
        }
    }

    private JdbcTemplate jdbcTemplate() {
        if (!jdbcEnabled) {
            return null;
        }
        return jdbcTemplateProvider.getIfAvailable();
    }

    private void ensureTable(JdbcTemplate template) {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            if (autoInit) {
                template.execute("""
                        CREATE TABLE IF NOT EXISTS ai_rag_ingestion_job (
                          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                          job_id VARCHAR(80) NOT NULL COMMENT 'RAG入库任务ID',
                          status VARCHAR(32) NOT NULL COMMENT '任务状态',
                          name VARCHAR(128) NOT NULL COMMENT '知识库名称',
                          tag VARCHAR(128) NOT NULL COMMENT '知识标签',
                          file_names_json TEXT NULL COMMENT '文件名列表',
                          total_bytes BIGINT NULL COMMENT '文件总大小',
                          error_message TEXT NULL COMMENT '失败原因',
                          created_at VARCHAR(32) NOT NULL COMMENT '创建时间',
                          updated_at VARCHAR(32) NOT NULL COMMENT '更新时间',
                          create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_job_id (job_id),
                          KEY idx_status (status),
                          KEY idx_create_time (create_time)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG异步入库任务表'
                        """);
            }
            initialized = true;
        }
    }

    private RagIngestionJob toRecord(String jobId,
                                     String status,
                                     String name,
                                     String tag,
                                     String fileNamesJson,
                                     Long totalBytes,
                                     String errorMessage,
                                     String createdAt,
                                     String updatedAt) {
        List<String> fileNames = StringUtils.hasText(fileNamesJson) ? JSON.parseArray(fileNamesJson, String.class) : List.of();
        return new RagIngestionJob(jobId, RagIngestionJobStatus.from(status), name, tag, fileNames,
                totalBytes == null ? 0L : totalBytes, errorMessage, createdAt, updatedAt);
    }

    private Long longNumber(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void logFallback(Exception e) {
        if (!unavailableLogged) {
            unavailableLogged = true;
            log.warn("RAG 入库任务落库不可用，已降级为内存任务：{}", e.getMessage());
        }
    }
}
