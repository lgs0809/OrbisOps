package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagMultimodalRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class RagMultimodalRepository implements IRagMultimodalRepository {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");
    private static final Pattern KNOWLEDGE_FILTER_PATTERN = Pattern.compile("knowledge\\s*==\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern KNOWLEDGE_SCOPE_FILTER_PATTERN = Pattern.compile("knowledge_scope\\s*==\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern PROJECT_ID_FILTER_PATTERN = Pattern.compile("project_id\\s*==\\s*['\"]([^'\"]+)['\"]");
    private static final int PGVECTOR_VECTOR_HNSW_MAX_DIMENSIONS = 2000;
    private static final int PGVECTOR_HALFVEC_HNSW_MAX_DIMENSIONS = 4000;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public RagMultimodalRepository(@Qualifier("pgVectorJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public boolean available() {
        return jdbcTemplateProvider.getIfAvailable() != null;
    }

    @Override
    public boolean ensureTable(String tableName, int dimension) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return false;
        }
        String table = qualifiedTableName(tableName);
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id TEXT PRIMARY KEY, "
                    + "content TEXT NOT NULL, "
                    + "metadata JSONB, "
                    + "embedding VECTOR(" + dimension + "), "
                    + "created_at TIMESTAMPTZ DEFAULT NOW(), "
                    + "updated_at TIMESTAMPTZ DEFAULT NOW())");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + indexNameSeed(tableName) + "_knowledge ON " + table + " ((metadata->>'knowledge'))");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + indexNameSeed(tableName) + "_knowledge_scope ON " + table + " ((metadata->>'knowledge_scope'))");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + indexNameSeed(tableName) + "_project_id ON " + table + " ((metadata->>'project_id'))");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + indexNameSeed(tableName) + "_source ON " + table + " ((metadata->>'source'))");
            ensureEmbeddingHnswIndex(jdbcTemplate, table, indexNameSeed(tableName), dimension);
            return true;
        } catch (Exception e) {
            log.warn("多模态向量表初始化失败，table={} reason={}", tableName, e.getMessage());
            return false;
        }
    }

    @Override
    public List<RagDocument> search(String tableName, String vectorLiteral, int dimension, String filterExpression, int topK, String provider, String model) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return List.of();
        }
        String distanceExpression = vectorDistanceExpression(dimension);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, content, metadata::text AS metadata, 1 - (")
                .append(distanceExpression)
                .append(") AS score FROM ")
                .append(qualifiedTableName(tableName))
                .append(" WHERE embedding IS NOT NULL");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(vectorLiteral);
        appendMetadataFilter(sql, args, parseMetadataFilter(filterExpression));
        sql.append(" ORDER BY ").append(distanceExpression).append(" LIMIT ?");
        args.add(vectorLiteral);
        args.add(topK);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));
            metadata.put("chunk_id", rs.getString("id"));
            metadata.put("retrieval_source", "multimodal");
            metadata.put("multimodal_provider", provider);
            metadata.put("multimodal_model", model);
            metadata.put("multimodal_vector_engine", vectorEngine(dimension));
            metadata.put("multimodal_score", rs.getDouble("score"));
            return new RagDocument(rs.getString("id"), rs.getString("content"), metadata);
        }, args.toArray());
    }

    @Override
    public void upsert(String tableName, int dimension, String id, String content, Map<String, Object> metadata, String vectorLiteral) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new IllegalStateException("PgVector JdbcTemplate 未初始化");
        }
        ensureTable(tableName, dimension);
        String sql = "INSERT INTO " + qualifiedTableName(tableName) + " (id, content, metadata, embedding, updated_at) "
                + "VALUES (?, ?, ?::jsonb, ?::vector, NOW()) "
                + "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding, updated_at = NOW()";
        jdbcTemplate.update(sql, id, content, JSON.toJSONString(metadata), vectorLiteral);
    }

    private String qualifiedTableName(String tableName) {
        String safeTableName = StringUtils.hasText(tableName) ? tableName.trim() : "vector_store_qwen_vl_multimodal";
        if (!TABLE_NAME_PATTERN.matcher(safeTableName).matches()) {
            throw new IllegalArgumentException("非法多模态向量表名：" + safeTableName);
        }
        return Arrays.stream(safeTableName.split("\\."))
                .map(part -> "\"" + part + "\"")
                .collect(Collectors.joining("."));
    }

    private String indexNameSeed(String tableName) {
        String safeTableName = StringUtils.hasText(tableName) ? tableName.trim() : "vector_store_qwen_vl_multimodal";
        return safeTableName.replace(".", "_").replaceAll("[^A-Za-z0-9_]", "_");
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
        if (StringUtils.hasText(filter.knowledge())) {
            sql.append(" AND metadata->>'knowledge' = ?");
            args.add(filter.knowledge());
        }
        if (StringUtils.hasText(filter.knowledgeScope())) {
            sql.append(" AND metadata->>'knowledge_scope' = ?");
            args.add(filter.knowledgeScope());
        }
        if (StringUtils.hasText(filter.projectId())) {
            sql.append(" AND metadata->>'project_id' = ?");
            args.add(filter.projectId());
        }
    }

    private void ensureEmbeddingHnswIndex(JdbcTemplate jdbcTemplate, String table, String seed, int dimension) {
        if (dimension <= PGVECTOR_VECTOR_HNSW_MAX_DIMENSIONS) {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + seed + "_embedding_hnsw ON " + table + " USING hnsw (embedding vector_cosine_ops)");
            return;
        }
        if (dimension <= PGVECTOR_HALFVEC_HNSW_MAX_DIMENSIONS) {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + seed + "_embedding_halfvec_hnsw ON " + table
                    + " USING hnsw ((embedding::halfvec(" + dimension + ")) halfvec_cosine_ops)");
            return;
        }
        throw new IllegalStateException("多模态向量 HNSW 索引无法创建，vector 上限="
                + PGVECTOR_VECTOR_HNSW_MAX_DIMENSIONS + " 维，halfvec 上限="
                + PGVECTOR_HALFVEC_HNSW_MAX_DIMENSIONS + " 维，当前维度=" + dimension);
    }

    private String vectorDistanceExpression(int dimension) {
        if (dimension <= PGVECTOR_VECTOR_HNSW_MAX_DIMENSIONS) {
            return "embedding <=> ?::vector";
        }
        if (dimension <= PGVECTOR_HALFVEC_HNSW_MAX_DIMENSIONS) {
            return "embedding::halfvec(" + dimension + ") <=> ?::halfvec(" + dimension + ")";
        }
        return "embedding <=> ?::vector";
    }

    private String vectorEngine(int dimension) {
        return dimension <= PGVECTOR_VECTOR_HNSW_MAX_DIMENSIONS ? "pgvector_vector_hnsw" : "pgvector_halfvec_hnsw";
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

    private record MetadataFilter(String knowledge, String knowledgeScope, String projectId) {
    }
}
