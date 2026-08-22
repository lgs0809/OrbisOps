package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.memory.SemanticLexicalRecallPort;
import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import cn.lgs.orbisops.domain.memory.model.SemanticMemoryRankedCandidate;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import cn.lgs.orbisops.domain.memory.service.SemanticLexicalQueryPolicy;
import cn.lgs.orbisops.domain.memory.service.SemanticMemoryPolicy;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** PostgreSQL FTS adapter for semantic-memory lexical recall. */
@Repository
public class OpsSemanticLexicalRecallAdapter implements SemanticLexicalRecallPort {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final Supplier<String> tableNameSupplier;
    private final SemanticMemoryPolicy semanticPolicy = new SemanticMemoryPolicy(
            new MemoryContentHashPolicy());
    private final SemanticLexicalQueryPolicy lexicalQueryPolicy = new SemanticLexicalQueryPolicy();

    @Autowired
    public OpsSemanticLexicalRecallAdapter(
            @Qualifier("pgVectorJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            @Value("${orbisops.rag.vector-table-name:orbisops_vector_store}") String vectorTableName) {
        this(jdbcTemplateProvider, () -> vectorTableName);
    }

    public OpsSemanticLexicalRecallAdapter(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                           Supplier<String> tableNameSupplier) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.tableNameSupplier = tableNameSupplier == null
                ? () -> "orbisops_vector_store"
                : tableNameSupplier;
    }

    @Override
    public List<SemanticMemoryRankedCandidate> recallLexical(String sessionId,
                                                             String userId,
                                                             String query,
                                                             int recallLimit) {
        JdbcTemplate template = jdbcTemplateProvider == null
                ? null
                : jdbcTemplateProvider.getIfAvailable();
        if (template == null) {
            return List.of();
        }
        String ftsQuery = lexicalQueryPolicy.webSearchQuery(query);
        if (!StringUtils.hasText(ftsQuery)) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id::text AS id, content, metadata::text AS metadata, ")
                .append("ts_rank_cd(to_tsvector('simple', coalesce(content, '')), websearch_to_tsquery('simple', ?)) AS lexical_rank ")
                .append("FROM ")
                .append(safeTableName(tableNameSupplier.get()))
                .append(" WHERE metadata->>'memory_type' = 'ops_chat'")
                .append(" AND metadata->>'session_id' = ?")
                .append(" AND metadata->>'memory_kind' = 'message'")
                .append(" AND content IS NOT NULL")
                .append(" AND to_tsvector('simple', coalesce(content, '')) @@ websearch_to_tsquery('simple', ?)");
        List<Object> args = new ArrayList<>();
        args.add(ftsQuery);
        args.add(sessionId);
        args.add(ftsQuery);
        if (StringUtils.hasText(userId)) {
            sql.append(" AND (metadata->>'user_id' = ? OR coalesce(metadata->>'user_id', '') = '')");
            args.add(userId);
        }
        sql.append(" ORDER BY lexical_rank DESC LIMIT ?");
        args.add(Math.max(1, Math.min(recallLimit, 100)));
        List<SemanticMemoryDocumentSnapshot> documents = template.query(
                sql.toString(),
                (rs, rowNum) -> {
                    Map<String, Object> metadata = new HashMap<>(
                            parseMetadata(rs.getString("metadata")));
                    metadata.put("memory_lexical_score", rs.getDouble("lexical_rank"));
                    metadata.put("memory_lexical_engine", "postgres_fts");
                    return semanticPolicy.inScope(metadata, sessionId, userId)
                            ? new SemanticMemoryDocumentSnapshot(
                                    rs.getString("id"),
                                    rs.getString("content"),
                                    metadata)
                            : null;
                },
                args.toArray())
                .stream()
                .filter(document -> document != null && semanticPolicy.active(document.metadata()))
                .toList();
        List<SemanticMemoryRankedCandidate> rankedDocuments = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            int rank = i + 1;
            SemanticMemoryDocumentSnapshot document = documents.get(i);
            Map<String, Object> metadata = new HashMap<>(document.metadata());
            metadata.put("memory_lexical_rank", rank);
            rankedDocuments.add(new SemanticMemoryRankedCandidate(
                    new SemanticMemoryDocumentSnapshot(document.id(), document.content(), metadata),
                    "lexical",
                    rank,
                    semanticPolicy.rrfScore(rank)));
        }
        return rankedDocuments;
    }

    private Map<String, Object> parseMetadata(String text) {
        if (!StringUtils.hasText(text)) {
            return Map.of();
        }
        try {
            return JSON.parseObject(text);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private String safeTableName(String tableName) {
        String value = StringUtils.hasText(tableName)
                ? tableName.trim()
                : "orbisops_vector_store";
        if (!value.matches("[A-Za-z0-9_\\.]+")) {
            return "orbisops_vector_store";
        }
        return value;
    }
}
