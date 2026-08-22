package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeDocumentCatalogRepository;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeChunkCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeDocumentCatalogEntry;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
public class JdbcKnowledgeDocumentCatalogRepository
        implements IKnowledgeDocumentCatalogRepository {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.knowledge-catalog.auto-init:true}")
    private boolean autoInit;

    private volatile boolean initialized;

    public JdbcKnowledgeDocumentCatalogRepository(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public void saveSubmitted(List<KnowledgeDocumentCatalogEntry> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return;
        }
        try {
            ensureTables(template);
            for (KnowledgeDocumentCatalogEntry document : documents) {
                if (document != null) {
                    upsertSubmitted(template, document);
                }
            }
        } catch (RuntimeException error) {
            log.warn("记录知识库导入文档目录失败 reason={}", error.getMessage());
        }
    }

    @Override
    public void synchronize(KnowledgeDocumentCatalogEntry document,
                            List<KnowledgeChunkCatalogEntry> chunks) {
        if (document == null) {
            return;
        }
        JdbcTemplate template = availableTemplate();
        if (template == null) {
            return;
        }
        try {
            ensureTables(template);
            upsertParsedDocument(template, document);
            if (chunks != null) {
                for (KnowledgeChunkCatalogEntry chunk : chunks) {
                    if (chunk != null) {
                        upsertChunk(template, chunk);
                    }
                }
            }
        } catch (RuntimeException error) {
            log.warn("同步知识库文档/片段目录失败 kbId={} documentId={} reason={}",
                    document.key().kbId(), document.documentId(), error.getMessage());
        }
    }

    private void upsertSubmitted(JdbcTemplate template,
                                 KnowledgeDocumentCatalogEntry document) {
        template.update("""
                        INSERT INTO ai_ops_knowledge_document
                        (document_id, kb_id, kb_scope, project_id, file_name, display_name, source_type,
                         document_type, file_size, parse_status, vector_status, chunk_count,
                         structure_preserved, ingestion_job_id, metadata_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          display_name=VALUES(display_name),
                          file_size=VALUES(file_size),
                          parse_status=VALUES(parse_status),
                          vector_status=VALUES(vector_status),
                          ingestion_job_id=VALUES(ingestion_job_id),
                          metadata_json=VALUES(metadata_json)
                        """,
                document.documentId(),
                document.key().kbId(),
                document.key().scope().name(),
                document.key().projectId(),
                document.fileName(),
                document.displayName(),
                document.sourceType(),
                document.documentType(),
                document.fileSize(),
                document.parseStatus(),
                document.vectorStatus(),
                document.chunkCount(),
                document.structurePreserved(),
                document.ingestionJobId(),
                JSON.toJSONString(document.metadata()));
    }

    private void upsertParsedDocument(JdbcTemplate template,
                                      KnowledgeDocumentCatalogEntry document) {
        template.update("""
                        INSERT INTO ai_ops_knowledge_document
                        (document_id, kb_id, kb_scope, project_id, file_name, display_name, source_type,
                         document_type, file_size, parse_status, vector_status, chunk_count,
                         structure_preserved, ingestion_job_id, metadata_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          display_name=VALUES(display_name),
                          document_type=VALUES(document_type),
                          parse_status=VALUES(parse_status),
                          vector_status=VALUES(vector_status),
                          chunk_count=VALUES(chunk_count),
                          structure_preserved=VALUES(structure_preserved),
                          metadata_json=VALUES(metadata_json)
                        """,
                document.documentId(),
                document.key().kbId(),
                document.key().scope().name(),
                document.key().projectId(),
                document.fileName(),
                document.displayName(),
                document.sourceType(),
                document.documentType(),
                document.fileSize(),
                document.parseStatus(),
                document.vectorStatus(),
                document.chunkCount(),
                document.structurePreserved(),
                document.ingestionJobId(),
                JSON.toJSONString(document.metadata()));
    }

    private void upsertChunk(JdbcTemplate template,
                             KnowledgeChunkCatalogEntry chunk) {
        template.update("""
                        INSERT INTO ai_ops_knowledge_chunk
                        (chunk_id, document_id, kb_id, kb_scope, project_id, chunk_index, chunk_strategy,
                         content_preview, parse_status, vector_status, metadata_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          chunk_index=VALUES(chunk_index),
                          chunk_strategy=VALUES(chunk_strategy),
                          content_preview=VALUES(content_preview),
                          parse_status=VALUES(parse_status),
                          vector_status=VALUES(vector_status),
                          metadata_json=VALUES(metadata_json)
                        """,
                chunk.chunkId(),
                chunk.documentId(),
                chunk.key().kbId(),
                chunk.key().scope().name(),
                chunk.key().projectId(),
                chunk.chunkIndex(),
                chunk.chunkStrategy(),
                chunk.contentPreview(),
                chunk.parseStatus(),
                chunk.vectorStatus(),
                JSON.toJSONString(chunk.metadata()));
    }

    private JdbcTemplate availableTemplate() {
        return jdbcTemplateProvider.getIfAvailable();
    }

    private void ensureTables(JdbcTemplate template) {
        if (initialized || !autoInit) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            template.execute("""
                    CREATE TABLE IF NOT EXISTS ai_ops_knowledge_document (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      document_id VARCHAR(128) NOT NULL COMMENT '文档ID',
                      kb_id VARCHAR(128) NOT NULL COMMENT '知识库ID',
                      kb_scope VARCHAR(24) NOT NULL COMMENT 'GLOBAL/PROJECT',
                      project_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '项目ID，GLOBAL为空',
                      file_name VARCHAR(512) NOT NULL DEFAULT '' COMMENT '原始文件名',
                      display_name VARCHAR(512) NOT NULL DEFAULT '' COMMENT '展示名称',
                      source_type VARCHAR(64) NOT NULL DEFAULT 'UPLOAD' COMMENT '来源类型',
                      document_type VARCHAR(64) NOT NULL DEFAULT '' COMMENT '文档类型',
                      file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小',
                      parse_status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED' COMMENT '解析状态',
                      vector_status VARCHAR(32) NOT NULL DEFAULT 'ASYNC' COMMENT '向量化状态',
                      chunk_count INT NOT NULL DEFAULT 0 COMMENT '已解析片段数',
                      structure_preserved TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否保留结构',
                      ingestion_job_id VARCHAR(80) NOT NULL DEFAULT '' COMMENT '入库任务ID',
                      metadata_json MEDIUMTEXT NULL COMMENT '元数据',
                      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_document_id (document_id),
                      KEY idx_scope_project_kb (kb_scope, project_id, kb_id),
                      KEY idx_job_id (ingestion_job_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维知识库文档目录表'
                    """);
            template.execute("""
                    CREATE TABLE IF NOT EXISTS ai_ops_knowledge_chunk (
                      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
                      chunk_id VARCHAR(160) NOT NULL COMMENT '片段ID',
                      document_id VARCHAR(128) NOT NULL COMMENT '文档ID',
                      kb_id VARCHAR(128) NOT NULL COMMENT '知识库ID',
                      kb_scope VARCHAR(24) NOT NULL COMMENT 'GLOBAL/PROJECT',
                      project_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '项目ID，GLOBAL为空',
                      chunk_index INT NOT NULL DEFAULT 0 COMMENT '片段序号',
                      chunk_strategy VARCHAR(80) NOT NULL DEFAULT '' COMMENT '切分策略',
                      content_preview TEXT NULL COMMENT '内容预览',
                      parse_status VARCHAR(32) NOT NULL DEFAULT 'READY' COMMENT '解析状态',
                      vector_status VARCHAR(32) NOT NULL DEFAULT 'VECTOR_PIPELINE' COMMENT '向量状态',
                      metadata_json MEDIUMTEXT NULL COMMENT '元数据',
                      create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                      update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_chunk_id (chunk_id),
                      KEY idx_document_id (document_id),
                      KEY idx_scope_project_kb (kb_scope, project_id, kb_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维知识库结构化片段目录表'
                    """);
            initialized = true;
        }
    }
}
