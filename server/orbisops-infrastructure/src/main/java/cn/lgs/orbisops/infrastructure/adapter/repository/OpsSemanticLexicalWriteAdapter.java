package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.memory.SemanticLexicalWritePort;
import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/** PostgreSQL adapter for FTS-only semantic-memory writes. */
@Repository
public class OpsSemanticLexicalWriteAdapter implements SemanticLexicalWritePort {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final Supplier<String> tableNameSupplier;

    @Autowired
    public OpsSemanticLexicalWriteAdapter(
            @Qualifier("pgVectorJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            @Value("${orbisops.rag.vector-table-name:orbisops_vector_store}") String vectorTableName) {
        this(jdbcTemplateProvider, () -> vectorTableName);
    }

    public OpsSemanticLexicalWriteAdapter(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                          Supplier<String> tableNameSupplier) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.tableNameSupplier = tableNameSupplier == null
                ? () -> "orbisops_vector_store"
                : tableNameSupplier;
    }

    @Override
    public void writeLexical(SemanticMemoryDocumentSnapshot document) {
        JdbcTemplate template = jdbcTemplateProvider == null
                ? null
                : jdbcTemplateProvider.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("PgVector JdbcTemplate 未初始化");
        }
        template.update(
                "INSERT INTO " + safeTableName(tableNameSupplier.get())
                        + " (id, content, metadata) VALUES (?::uuid, ?, ?::jsonb)"
                        + " ON CONFLICT (id) DO UPDATE SET content=EXCLUDED.content, metadata=EXCLUDED.metadata",
                document.id(),
                document.content(),
                JSON.toJSONString(document.metadata()));
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
