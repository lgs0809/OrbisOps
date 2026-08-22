package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.memory.SemanticMemoryClearPersistencePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/** PostgreSQL adapter for clearing one semantic-memory session. */
@Repository
public class OpsSemanticMemoryClearPersistenceAdapter implements SemanticMemoryClearPersistencePort {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final Supplier<String> tableNameSupplier;

    @Autowired
    public OpsSemanticMemoryClearPersistenceAdapter(
            @Qualifier("pgVectorJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            @Value("${orbisops.rag.vector-table-name:orbisops_vector_store}") String vectorTableName) {
        this(jdbcTemplateProvider, () -> vectorTableName);
    }

    public OpsSemanticMemoryClearPersistenceAdapter(
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            Supplier<String> tableNameSupplier) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.tableNameSupplier = tableNameSupplier == null
                ? () -> "orbisops_vector_store"
                : tableNameSupplier;
    }

    @Override
    public boolean clearSession(String sessionId) {
        JdbcTemplate template = jdbcTemplateProvider == null
                ? null
                : jdbcTemplateProvider.getIfAvailable();
        if (template == null) {
            return false;
        }
        template.update(
                "DELETE FROM " + safeTableName(tableNameSupplier.get())
                        + " WHERE metadata->>'memory_type' = 'ops_chat'"
                        + " AND metadata->>'session_id' = ?",
                sessionId);
        return true;
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
