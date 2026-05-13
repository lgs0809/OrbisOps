package cn.lgs.orbisops.infrastructure.adapter.resourcehealth;

import cn.lgs.orbisops.application.rag.RagOrderCatalogPort;
import cn.lgs.orbisops.application.resourcehealth.PgVectorResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthCheck;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** JDBC probe for PgVector connectivity, extension and indexed chunk metadata. */
@Repository
public class JdbcPgVectorResourceHealthProbeAdapter
        implements PgVectorResourceHealthProbePort {

    private final JdbcTemplate jdbcTemplate;
    private final RagOrderCatalogPort ragOrderRepository;

    public JdbcPgVectorResourceHealthProbeAdapter(
            @Qualifier("pgVectorJdbcTemplate")
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<RagOrderCatalogPort> ragOrderRepositoryProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.ragOrderRepository = ragOrderRepositoryProvider.getIfAvailable();
    }

    @Override
    public ResourceHealthCheck probe(String tableName) {
        String configuredTable = text(tableName, "orbisops_vector_store");
        if (jdbcTemplate == null) {
            return ResourceHealthCheck.unavailable(
                    "pgvector",
                    "PgVector 知识库",
                    configuredTable,
                    "JdbcTemplate 未初始化");
        }
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        } catch (RuntimeException error) {
            return ResourceHealthCheck.unavailable(
                    "pgvector",
                    "PgVector 知识库",
                    configuredTable,
                    message("连接检查失败", error));
        }

        Map<String, Object> details = new LinkedHashMap<>();
        String message = "连接正常";
        try {
            String qualifiedTable = qualify(configuredTable);
            Long chunks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM " + qualifiedTable
                            + " WHERE content IS NOT NULL",
                    Long.class);
            details.put("chunkCount", chunks == null ? 0L : chunks);
            details.put(
                    "vectorExtension",
                    jdbcTemplate.queryForList(
                            "SELECT extversion FROM pg_extension WHERE extname = 'vector'"));
            details.put(
                    "embeddingDimension",
                    firstValue(jdbcTemplate.queryForList(
                            "SELECT vector_dims(embedding) AS dim FROM "
                                    + qualifiedTable
                                    + " WHERE embedding IS NOT NULL LIMIT 1"),
                            "dim"));
            details.put(
                    "knowledgeBaseCount",
                    ragOrderRepository == null
                            ? null
                            : ragOrderRepository.queryAll().size());
        } catch (RuntimeException error) {
            message += "；chunk 统计失败：" + errorMessage(error);
        }
        return new ResourceHealthCheck(
                "pgvector",
                "PgVector 知识库",
                configuredTable,
                true,
                message,
                details);
    }

    private Object firstValue(List<Map<String, Object>> rows, String key) {
        if (rows == null || rows.isEmpty()) return null;
        return rows.get(0).get(key);
    }

    private String qualify(String tableName) {
        return Arrays.stream(tableName.split("\\."))
                .map(part -> "\"" + part.replace("\"", "") + "\"")
                .collect(Collectors.joining("."));
    }

    private String message(String prefix, RuntimeException error) {
        return prefix + "：" + errorMessage(error);
    }

    private String errorMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message.trim();
    }

    private String text(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        return text.isBlank() ? fallback : text;
    }
}
