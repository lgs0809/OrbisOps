package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagKnowledgeDocumentRecord;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagLexicalChunkRecord;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class RagKnowledgeRepository implements IRagKnowledgeRepository {

    private static final Pattern VECTOR_TABLE_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");
    private static final Pattern KNOWLEDGE_FILTER_PATTERN = Pattern.compile("knowledge\\s*==\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern KNOWLEDGE_SCOPE_FILTER_PATTERN = Pattern.compile("knowledge_scope\\s*==\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern PROJECT_ID_FILTER_PATTERN = Pattern.compile("project_id\\s*==\\s*['\"]([^'\"]+)['\"]");
    private static final int PGVECTOR_VECTOR_HNSW_MAX_DIMENSIONS = 2000;
    private static final int PGVECTOR_HALFVEC_HNSW_MAX_DIMENSIONS = 4000;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @Value("${orbisops.rag.vector-table-name:orbisops_vector_store}")
    private String ragVectorTableName;

    @Value("${orbisops.rag.vector-index.auto-init:true}")
    private boolean vectorIndexAutoInit;

    @Value("${spring.ai.openai.embedding.options.dimensions:2048}")
    private int embeddingDimensions;

    private volatile boolean vectorIndexesInitialized;

    public RagKnowledgeRepository(@Qualifier("pgVectorJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public boolean available() {
        return jdbcTemplateProvider.getIfAvailable() != null;
    }

    @Override
    public List<Map<String, Object>> listKnowledgeStats() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return List.of();
        }
        StringBuilder where = new StringBuilder(" WHERE content IS NOT NULL");
        List<Object> args = new ArrayList<>();
        // The aggregate catalog is the global knowledge boundary. Legacy rows
        // created before scope metadata existed are global only when they also
        // have no project id; project rows must never be promoted into it.
        appendScopeFilter(where, args, "GLOBAL");
        return jdbcTemplate.query("""
                        SELECT coalesce(metadata->>'knowledge', 'default') AS knowledge_tag,
                               count(*) AS chunk_count,
                               count(DISTINCT coalesce(metadata->>'source', metadata->>'file_name', id::text)) AS document_count
                        FROM %s
                        %s
                        GROUP BY coalesce(metadata->>'knowledge', 'default')
                        ORDER BY chunk_count DESC
                        """.formatted(qualifiedVectorTableName(), where),
                (rs, rowNum) -> Map.of(
                        "knowledgeTag", rs.getString("knowledge_tag"),
                        "chunkCount", rs.getLong("chunk_count"),
                        "documentCount", rs.getLong("document_count")),
                args.toArray());
    }

    @Override
    public Map<String, Object> documentStats(String tag) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return Map.of("chunkCount", 0L, "documentCount", 0L, "byType", List.of(), "bySource", List.of());
        }
        List<Object> args = new ArrayList<>();
        String where = " WHERE content IS NOT NULL";
        if (StringUtils.hasText(tag)) {
            where += " AND metadata->>'knowledge' = ?";
            args.add(tag);
        }
        Map<String, Object> summary = jdbcTemplate.queryForObject("""
                        SELECT count(*) AS chunk_count,
                               count(DISTINCT coalesce(metadata->>'source', metadata->>'file_name', id::text)) AS document_count
                        FROM %s
                        %s
                        """.formatted(qualifiedVectorTableName(), where),
                (rs, rowNum) -> Map.of("chunkCount", rs.getLong("chunk_count"), "documentCount", rs.getLong("document_count")),
                args.toArray());
        List<Map<String, Object>> byType = jdbcTemplate.query("""
                        SELECT coalesce(metadata->>'document_type', 'unknown') AS key, count(*) AS count
                        FROM %s
                        %s
                        GROUP BY coalesce(metadata->>'document_type', 'unknown')
                        ORDER BY count DESC
                        LIMIT 20
                        """.formatted(qualifiedVectorTableName(), where),
                (rs, rowNum) -> Map.of("key", rs.getString("key"), "count", rs.getLong("count")),
                args.toArray());
        List<Map<String, Object>> bySource = jdbcTemplate.query("""
                        SELECT coalesce(metadata->>'source', metadata->>'file_name', 'unknown') AS key, count(*) AS count
                        FROM %s
                        %s
                        GROUP BY coalesce(metadata->>'source', metadata->>'file_name', 'unknown')
                        ORDER BY count DESC
                        LIMIT 20
                        """.formatted(qualifiedVectorTableName(), where),
                (rs, rowNum) -> Map.of("key", rs.getString("key"), "count", rs.getLong("count")),
                args.toArray());
        java.util.HashMap<String, Object> data = new java.util.HashMap<>(summary == null ? Map.of() : summary);
        data.put("byType", byType);
        data.put("bySource", bySource);
        data.put("tag", StringUtils.hasText(tag) ? tag : "");
        return data;
    }

    @Override
    public Map<String, Object> documentStats(String tag, String scope, String projectId) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return Map.of("chunkCount", 0L, "documentCount", 0L, "byType", List.of(), "bySource", List.of());
        }
        MetadataFilter metadataFilter = new MetadataFilter(
                tag,
                normalizedScope(scope),
                normalizedProjectId(scope, projectId));
        StringBuilder where = new StringBuilder(" WHERE content IS NOT NULL");
        List<Object> args = new ArrayList<>();
        appendMetadataFilter(where, args, metadataFilter);
        Map<String, Object> summary = jdbcTemplate.queryForObject("""
                        SELECT count(*) AS chunk_count,
                               count(DISTINCT coalesce(metadata->>'source', metadata->>'file_name', id::text)) AS document_count
                        FROM %s
                        %s
                        """.formatted(qualifiedVectorTableName(), where),
                (rs, rowNum) -> Map.of("chunkCount", rs.getLong("chunk_count"), "documentCount", rs.getLong("document_count")),
                args.toArray());
        List<Map<String, Object>> byType = jdbcTemplate.query("""
                        SELECT coalesce(metadata->>'document_type', 'unknown') AS key, count(*) AS count
                        FROM %s
                        %s
                        GROUP BY coalesce(metadata->>'document_type', 'unknown')
                        ORDER BY count DESC
                        LIMIT 20
                        """.formatted(qualifiedVectorTableName(), where),
                (rs, rowNum) -> Map.of("key", rs.getString("key"), "count", rs.getLong("count")),
                args.toArray());
        List<Map<String, Object>> bySource = jdbcTemplate.query("""
                        SELECT coalesce(metadata->>'source', metadata->>'file_name', 'unknown') AS key, count(*) AS count
                        FROM %s
                        %s
                        GROUP BY coalesce(metadata->>'source', metadata->>'file_name', 'unknown')
                        ORDER BY count DESC
                        LIMIT 20
                        """.formatted(qualifiedVectorTableName(), where),
                (rs, rowNum) -> Map.of("key", rs.getString("key"), "count", rs.getLong("count")),
                args.toArray());
        HashMap<String, Object> data = new HashMap<>(summary == null ? Map.of() : summary);
        data.put("byType", byType);
        data.put("bySource", bySource);
        data.put("tag", StringUtils.hasText(tag) ? tag : "");
        data.put("scope", normalizedScope(scope));
        data.put("projectId", normalizedProjectId(scope, projectId));
        return data;
    }

    @Override
    public boolean deleteChunk(String chunkId) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        String decoded = URLDecoder.decode(chunkId, StandardCharsets.UTF_8);
        return jdbcTemplate.update("DELETE FROM " + qualifiedVectorTableName() + " WHERE id::text = ?", decoded) > 0;
    }

    @Override
    public boolean deleteChunk(String chunkId, String tag, String scope, String projectId) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        String decoded = URLDecoder.decode(chunkId, StandardCharsets.UTF_8);
        StringBuilder sql = new StringBuilder("DELETE FROM ")
                .append(qualifiedVectorTableName())
                .append(" WHERE id::text = ?");
        List<Object> args = new ArrayList<>();
        args.add(decoded);
        appendMetadataFilter(sql, args, new MetadataFilter(tag, normalizedScope(scope), normalizedProjectId(scope, projectId)));
        return jdbcTemplate.update(sql.toString(), args.toArray()) > 0;
    }

    @Override
    public Map<String, Object> deleteChunksByTag(String tag) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        String decoded = URLDecoder.decode(tag, StandardCharsets.UTF_8);
        int rows = jdbcTemplate.update("DELETE FROM " + qualifiedVectorTableName() + " WHERE metadata->>'knowledge' = ?", decoded);
        return Map.of("knowledgeTag", decoded, "deletedChunks", rows);
    }

    @Override
    public Map<String, Object> deleteChunksByTag(String tag, String scope, String projectId) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        String decoded = URLDecoder.decode(tag, StandardCharsets.UTF_8);
        String normalizedScope = normalizedScope(scope);
        String normalizedProjectId = normalizedProjectId(scope, projectId);
        StringBuilder sql = new StringBuilder("DELETE FROM ")
                .append(qualifiedVectorTableName())
                .append(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        appendMetadataFilter(sql, args, new MetadataFilter(decoded, normalizedScope, normalizedProjectId));
        int rows = jdbcTemplate.update(sql.toString(), args.toArray());
        Map<String, Object> result = new HashMap<>();
        result.put("knowledgeTag", decoded);
        result.put("scope", normalizedScope);
        result.put("projectId", normalizedProjectId);
        result.put("deletedChunks", rows);
        return result;
    }

    @Override
    public List<RagKnowledgeDocumentRecord> listDocuments(String tag) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("SELECT id::text AS id, content, metadata::text AS metadata FROM ")
                .append(qualifiedVectorTableName())
                .append(" WHERE content IS NOT NULL");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(tag)) {
            sql.append(" AND metadata->>'knowledge' = ?");
            args.add(tag);
        }
        sql.append(" ORDER BY id DESC LIMIT 200");
        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new RagKnowledgeDocumentRecord(rs.getString("id"), rs.getString("content"), rs.getString("metadata")),
                args.toArray());
    }

    @Override
    public List<RagKnowledgeDocumentRecord> listDocuments(String tag, String scope, String projectId) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("SELECT id::text AS id, content, metadata::text AS metadata FROM ")
                .append(qualifiedVectorTableName())
                .append(" WHERE content IS NOT NULL");
        List<Object> args = new ArrayList<>();
        appendMetadataFilter(sql, args, new MetadataFilter(
                tag,
                normalizedScope(scope),
                normalizedProjectId(scope, projectId)));
        sql.append(" ORDER BY id DESC LIMIT 500");
        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new RagKnowledgeDocumentRecord(rs.getString("id"), rs.getString("content"), rs.getString("metadata")),
                args.toArray());
    }

    @Override
    public RagKnowledgeDocumentRecord documentContent(String chunkId) {
        JdbcTemplate jdbcTemplate = requireJdbcTemplate();
        String decoded = URLDecoder.decode(chunkId, StandardCharsets.UTF_8);
        List<RagKnowledgeDocumentRecord> documents = jdbcTemplate.query("""
                        SELECT id::text AS id, content, metadata::text AS metadata
                        FROM %s
                        WHERE id::text = ?
                        LIMIT 1
                        """.formatted(qualifiedVectorTableName()),
                (rs, rowNum) -> new RagKnowledgeDocumentRecord(rs.getString("id"), rs.getString("content"), rs.getString("metadata")),
                decoded);
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("知识库 chunk 不存在：" + decoded);
        }
        return documents.get(0);
    }

    @Override
    public List<RagDocument> searchVectorCandidates(String filterExpression, String vectorLiteral, int vectorDimensions, int topK) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null || !StringUtils.hasText(vectorLiteral)) {
            return List.of();
        }
        ensureVectorSearchIndexes(jdbcTemplate);
        int dimensions = vectorDimensions > 0 ? vectorDimensions : embeddingDimensions;
        MetadataFilter metadataFilter = parseMetadataFilter(filterExpression);
        String distanceExpression = vectorDistanceExpression(dimensions);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id::text AS id, content, metadata::text AS metadata, 1 - (")
                .append(distanceExpression)
                .append(") AS score FROM ")
                .append(qualifiedVectorTableName())
                .append(" WHERE embedding IS NOT NULL");

        List<Object> args = new ArrayList<>();
        args.add(vectorLiteral);
        appendMetadataFilter(sql, args, metadataFilter);
        sql.append(" ORDER BY ").append(distanceExpression).append(" LIMIT ?");
        args.add(vectorLiteral);
        args.add(clamp(topK, 1, 50));

        try {
            return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
                Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));
                metadata.put("chunk_id", rs.getString("id"));
                metadata.put("retrieval_source", "vector");
                metadata.put("vector_engine", vectorEngine(dimensions));
                metadata.put("vector_store", ragVectorTableName);
                metadata.put("vector_score", rs.getDouble("score"));
                return new RagDocument(rs.getString("id"), rs.getString("content"), metadata);
            }, args.toArray());
        } catch (Exception e) {
            throw new IllegalStateException("文本向量检索失败，dimension=" + dimensions
                    + "，2048 维需要 pgvector halfvec/HNSW 支持：" + e.getMessage(), e);
        }
    }

    @Override
    public List<RagLexicalChunkRecord> searchLexicalCandidates(String filterExpression, List<String> candidateTerms, int candidateLimit) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null || candidateTerms == null || candidateTerms.isEmpty()) {
            return List.of();
        }
        List<String> terms = candidateTerms.stream()
                .filter(StringUtils::hasText)
                .map(term -> term.toLowerCase(Locale.ROOT))
                .distinct()
                .limit(16)
                .toList();
        if (terms.isEmpty()) {
            return List.of();
        }
        ensureVectorSearchIndexes(jdbcTemplate);
        List<RagLexicalChunkRecord> ftsChunks = loadPostgresFtsChunks(jdbcTemplate, filterExpression, terms, candidateLimit);
        if (!ftsChunks.isEmpty()) {
            return ftsChunks;
        }
        return loadLikeChunks(jdbcTemplate, filterExpression, terms, candidateLimit);
    }

    @Override
    public void persistParsedDocument(String name,
                                      String tag,
                                      String fileName,
                                      String contentType,
                                      long fileSize,
                                      List<RagDocument> documents) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null || documents == null || documents.isEmpty()) {
            return;
        }
        ensureKnowledgeTables(jdbcTemplate);
        ensureVectorSearchIndexes(jdbcTemplate);
        String safeFileName = StringUtils.hasText(fileName) ? fileName : "unknown";
        Map<String, Object> firstMetadata = documents.get(0).metadata();
        String physicalKnowledgeId = physicalKnowledgeId(tag, firstMetadata);
        String documentId = UUID.nameUUIDFromBytes((physicalKnowledgeId + ":" + name + ":" + safeFileName + ":" + fileSize)
                .getBytes(StandardCharsets.UTF_8)).toString();
        jdbcTemplate.update("""
                        INSERT INTO knowledge_base (id, name, tag, metadata)
                        VALUES (?, ?, ?, ?::jsonb)
                        ON CONFLICT (id) DO UPDATE SET
                          name = EXCLUDED.name,
                          tag = EXCLUDED.tag,
                          metadata = EXCLUDED.metadata,
                          updated_at = NOW()
                        """,
                physicalKnowledgeId,
                name,
                tag,
                JSON.toJSONString(firstMetadata));
        jdbcTemplate.update("DELETE FROM knowledge_chunk WHERE document_id = ?", documentId);
        jdbcTemplate.update("""
                        INSERT INTO knowledge_document
                        (id, knowledge_id, file_name, content_type, document_type, parser_version, chunk_count, status, metadata)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'READY', ?::jsonb)
                        ON CONFLICT (id) DO UPDATE SET
                          knowledge_id = EXCLUDED.knowledge_id,
                          file_name = EXCLUDED.file_name,
                          content_type = EXCLUDED.content_type,
                          document_type = EXCLUDED.document_type,
                          parser_version = EXCLUDED.parser_version,
                          chunk_count = EXCLUDED.chunk_count,
                          status = EXCLUDED.status,
                          metadata = EXCLUDED.metadata,
                          updated_at = NOW()
                        """,
                documentId,
                physicalKnowledgeId,
                safeFileName,
                contentType,
                stringValue(firstMetadata.get("document_type")),
                stringValue(firstMetadata.get("parser_version")),
                documents.size(),
                JSON.toJSONString(firstMetadata));
        for (RagDocument document : documents) {
            Map<String, Object> metadata = document.metadata();
            jdbcTemplate.update("""
                            INSERT INTO knowledge_chunk
                            (id, document_id, knowledge_id, chunk_index, vector_table, vector_id, content_preview, metadata)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                            ON CONFLICT (id) DO UPDATE SET
                              content_preview = EXCLUDED.content_preview,
                              metadata = EXCLUDED.metadata,
                              updated_at = NOW()
                            """,
                    document.id(),
                    documentId,
                    physicalKnowledgeId,
                    intValue(metadata.get("chunk_index")),
                    ragVectorTableName,
                    document.id(),
                    preview(document.text()),
                    JSON.toJSONString(metadata));
        }
    }

    private List<RagLexicalChunkRecord> loadPostgresFtsChunks(JdbcTemplate jdbcTemplate, String filterExpression, List<String> candidateTerms, int candidateLimit) {
        MetadataFilter metadataFilter = parseMetadataFilter(filterExpression);
        String ftsQuery = candidateTerms.stream().limit(12).collect(Collectors.joining(" OR "));
        if (!StringUtils.hasText(ftsQuery)) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id::text AS id, content, metadata::text AS metadata, ")
                .append("ts_rank_cd(to_tsvector('simple', coalesce(content, '')), websearch_to_tsquery('simple', ?)) AS lexical_rank ")
                .append("FROM ")
                .append(qualifiedVectorTableName())
                .append(" WHERE content IS NOT NULL ")
                .append("AND to_tsvector('simple', coalesce(content, '')) @@ websearch_to_tsquery('simple', ?)");
        List<Object> args = new ArrayList<>();
        args.add(ftsQuery);
        args.add(ftsQuery);
        appendMetadataFilter(sql, args, metadataFilter);
        sql.append(" ORDER BY lexical_rank DESC LIMIT ?");
        args.add(clamp(candidateLimit, 50, 2000));

        try {
            return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
                Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));
                metadata.put("chunk_id", rs.getString("id"));
                metadata.put("retrieval_source", "bm25");
                metadata.put("lexical_engine", "postgres_fts");
                metadata.put("lexical_rank", rs.getDouble("lexical_rank"));
                metadata.put("lexical_store", ragVectorTableName);
                return new RagLexicalChunkRecord(rs.getString("id"), rs.getString("content"), metadata);
            }, args.toArray());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<RagLexicalChunkRecord> loadLikeChunks(JdbcTemplate jdbcTemplate, String filterExpression, List<String> candidateTerms, int candidateLimit) {
        MetadataFilter metadataFilter = parseMetadataFilter(filterExpression);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id::text AS id, content, metadata::text AS metadata FROM ")
                .append(qualifiedVectorTableName())
                .append(" WHERE content IS NOT NULL");
        List<Object> args = new ArrayList<>();

        appendMetadataFilter(sql, args, metadataFilter);

        sql.append(" AND (");
        for (int i = 0; i < candidateTerms.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("lower(content) LIKE ? ESCAPE '\\'");
            args.add("%" + escapeLike(candidateTerms.get(i).toLowerCase(Locale.ROOT)) + "%");
        }
        sql.append(") LIMIT ?");
        args.add(clamp(candidateLimit, 50, 2000));

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));
            metadata.put("chunk_id", rs.getString("id"));
            metadata.put("retrieval_source", "bm25");
            metadata.put("lexical_store", ragVectorTableName);
            return new RagLexicalChunkRecord(rs.getString("id"), rs.getString("content"), metadata);
        }, args.toArray());
    }

    private JdbcTemplate requireJdbcTemplate() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new IllegalStateException("PgVector JdbcTemplate 未初始化");
        }
        return jdbcTemplate;
    }

    private String qualifiedVectorTableName() {
        String tableName = StringUtils.hasText(ragVectorTableName) ? ragVectorTableName.trim() : "orbisops_vector_store";
        if (!VECTOR_TABLE_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("非法 PgVector 表名：" + tableName);
        }
        return Arrays.stream(tableName.split("\\."))
                .map(part -> "\"" + part + "\"")
                .collect(Collectors.joining("."));
    }

    private MetadataFilter parseMetadataFilter(String filterExpression) {
        if (!StringUtils.hasText(filterExpression)) {
            return new MetadataFilter(null, null, null);
        }
        return new MetadataFilter(
                matchFilter(KNOWLEDGE_FILTER_PATTERN, filterExpression),
                matchFilter(KNOWLEDGE_SCOPE_FILTER_PATTERN, filterExpression),
                matchFilter(PROJECT_ID_FILTER_PATTERN, filterExpression));
    }

    private String matchFilter(Pattern pattern, String filterExpression) {
        java.util.regex.Matcher matcher = pattern.matcher(filterExpression);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void appendMetadataFilter(StringBuilder sql, List<Object> args, MetadataFilter filter) {
        if (filter == null) {
            return;
        }
        if (StringUtils.hasText(filter.knowledge())) {
            sql.append(" AND metadata->>'knowledge' = ?");
            args.add(filter.knowledge());
        }
        appendScopeFilter(sql, args, filter.knowledgeScope());
        if (StringUtils.hasText(filter.projectId())) {
            sql.append(" AND metadata->>'project_id' = ?");
            args.add(filter.projectId());
        }
    }

    private void appendScopeFilter(StringBuilder sql, List<Object> args, String scope) {
        if (!StringUtils.hasText(scope)) {
            return;
        }
        if ("GLOBAL".equalsIgnoreCase(scope)) {
            // Rows written by the legacy importer have no scope metadata. Treat
            // only rows without both scope and project identity as global so a
            // malformed project row cannot cross the project isolation boundary.
            sql.append(" AND (metadata->>'knowledge_scope' = ?");
            args.add("GLOBAL");
            sql.append(" OR (metadata->>'knowledge_scope' IS NULL AND metadata->>'project_id' IS NULL))");
            return;
        }
        sql.append(" AND metadata->>'knowledge_scope' = ?");
        args.add(scope);
    }

    private String normalizedScope(String scope) {
        if ("PROJECT".equalsIgnoreCase(scope)) {
            return "PROJECT";
        }
        if ("GLOBAL".equalsIgnoreCase(scope)) {
            return "GLOBAL";
        }
        return null;
    }

    private String normalizedProjectId(String scope, String projectId) {
        return "PROJECT".equalsIgnoreCase(scope) && StringUtils.hasText(projectId)
                ? projectId.trim()
                : null;
    }

    private String physicalKnowledgeId(String tag, Map<String, Object> metadata) {
        String scope = String.valueOf(metadata.getOrDefault("knowledge_scope", "GLOBAL"));
        String projectId = String.valueOf(metadata.getOrDefault("project_id", ""));
        return "PROJECT".equalsIgnoreCase(scope) && StringUtils.hasText(projectId)
                ? "PROJECT:" + projectId.trim() + ":" + tag
                : "GLOBAL:" + tag;
    }

    private Map<String, Object> parseMetadata(String metadataText) {
        if (!StringUtils.hasText(metadataText)) {
            return new HashMap<>();
        }
        try {
            JSONObject metadata = JSON.parseObject(metadataText);
            return metadata == null ? new HashMap<>() : new HashMap<>(metadata);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private void ensureKnowledgeTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_base (
                  id TEXT PRIMARY KEY,
                  name TEXT NOT NULL,
                  tag TEXT NOT NULL,
                  metadata JSONB,
                  created_at TIMESTAMPTZ DEFAULT NOW(),
                  updated_at TIMESTAMPTZ DEFAULT NOW()
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_document (
                  id TEXT PRIMARY KEY,
                  knowledge_id TEXT NOT NULL,
                  file_name TEXT NOT NULL,
                  content_type TEXT,
                  document_type TEXT,
                  parser_version TEXT,
                  chunk_count INT NOT NULL DEFAULT 0,
                  status TEXT NOT NULL DEFAULT 'READY',
                  metadata JSONB,
                  created_at TIMESTAMPTZ DEFAULT NOW(),
                  updated_at TIMESTAMPTZ DEFAULT NOW()
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_chunk (
                  id TEXT PRIMARY KEY,
                  document_id TEXT NOT NULL,
                  knowledge_id TEXT NOT NULL,
                  chunk_index INT,
                  vector_table TEXT NOT NULL,
                  vector_id TEXT NOT NULL,
                  content_preview TEXT,
                  metadata JSONB,
                  created_at TIMESTAMPTZ DEFAULT NOW(),
                  updated_at TIMESTAMPTZ DEFAULT NOW()
                )
                """);
    }

    private void ensureVectorSearchIndexes(JdbcTemplate jdbcTemplate) {
        if (!vectorIndexAutoInit || vectorIndexesInitialized) {
            return;
        }
        synchronized (this) {
            if (vectorIndexesInitialized) {
                return;
            }
            try {
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
                String table = qualifiedVectorTableName();
                String seed = vectorIndexNameSeed();
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + seed + "_knowledge ON " + table + " ((metadata->>'knowledge'))");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + seed + "_knowledge_scope ON " + table + " ((metadata->>'knowledge_scope'))");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + seed + "_project_id ON " + table + " ((metadata->>'project_id'))");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + seed + "_memory_type ON " + table + " ((metadata->>'memory_type'))");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + seed + "_session ON " + table + " ((metadata->>'session_id'))");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + seed + "_content_trgm ON " + table + " USING gin (lower(content) gin_trgm_ops)");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + seed + "_content_fts ON " + table + " USING gin (to_tsvector('simple', coalesce(content, '')))");
                ensureEmbeddingHnswIndex(jdbcTemplate, table, seed, embeddingDimensions);
                vectorIndexesInitialized = true;
            } catch (Exception e) {
                log.warn("RAG 文本向量检索索引初始化失败，已跳过：{}", e.getMessage());
            }
        }
    }

    private void ensureEmbeddingHnswIndex(JdbcTemplate jdbcTemplate, String table, String seed, int dimensions) {
        try {
            if (dimensions <= PGVECTOR_VECTOR_HNSW_MAX_DIMENSIONS) {
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + seed + "_embedding_hnsw ON " + table + " USING hnsw (embedding vector_cosine_ops)");
                return;
            }
            if (dimensions <= PGVECTOR_HALFVEC_HNSW_MAX_DIMENSIONS) {
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + seed + "_embedding_halfvec_hnsw ON " + table
                        + " USING hnsw ((embedding::halfvec(" + dimensions + ")) halfvec_cosine_ops)");
                return;
            }
            log.warn("文本向量 HNSW 索引无法创建，vector 上限={} 维，halfvec 上限={} 维，当前维度={}",
                    PGVECTOR_VECTOR_HNSW_MAX_DIMENSIONS, PGVECTOR_HALFVEC_HNSW_MAX_DIMENSIONS, dimensions);
        } catch (Exception e) {
            throw new IllegalStateException("文本向量 HNSW 索引创建失败，dimension=" + dimensions
                    + "，请确认 pgvector >= 0.7 且支持 halfvec：" + e.getMessage(), e);
        }
    }

    private String vectorDistanceExpression(int dimensions) {
        if (dimensions <= PGVECTOR_VECTOR_HNSW_MAX_DIMENSIONS) {
            return "embedding <=> ?::vector";
        }
        if (dimensions <= PGVECTOR_HALFVEC_HNSW_MAX_DIMENSIONS) {
            return "embedding::halfvec(" + dimensions + ") <=> ?::halfvec(" + dimensions + ")";
        }
        return "embedding <=> ?::vector";
    }

    private String vectorEngine(int dimensions) {
        return dimensions <= PGVECTOR_VECTOR_HNSW_MAX_DIMENSIONS ? "pgvector_vector_hnsw" : "pgvector_halfvec_hnsw";
    }

    private String vectorIndexNameSeed() {
        String tableName = StringUtils.hasText(ragVectorTableName) ? ragVectorTableName.trim() : "orbisops_vector_store";
        return tableName.replace(".", "_").replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private record MetadataFilter(String knowledge, String knowledgeScope, String projectId) {
    }
}
