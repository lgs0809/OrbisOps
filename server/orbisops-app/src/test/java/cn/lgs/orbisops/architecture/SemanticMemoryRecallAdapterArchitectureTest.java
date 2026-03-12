package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticMemoryRecallAdapterArchitectureTest {

    private static final String STORE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsPgVectorSemanticMemoryStore.java";
    private static final String VECTOR_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsSemanticVectorRecallAdapter.java";
    private static final String LEXICAL_ADAPTER = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/OpsSemanticLexicalRecallAdapter.java";
    private static final String FAILURE_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsSemanticRetrievalFailureAdapter.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void vectorAdapterIsTheOnlyOwnerOfVectorRecallInfrastructure() throws IOException {
        String store = read(STORE);
        String adapter = read(VECTOR_ADAPTER);

        assertAll(
                () -> assertTrue(adapter.contains("implements SemanticVectorRecallPort")),
                () -> assertTrue(adapter.contains("ObjectProvider<VectorStore>")),
                () -> assertTrue(adapter.contains("SearchRequest.builder()")),
                () -> assertTrue(adapter.contains("FilterExpressionTextParser")),
                () -> assertTrue(adapter.contains("similaritySearch(")),
                () -> assertTrue(adapter.contains("semanticPolicy.inScope(")),
                () -> assertTrue(adapter.contains("semanticPolicy.rrfScore(")),
                () -> assertFalse(adapter.contains("JdbcTemplate")),
                () -> assertFalse(adapter.contains("websearch_to_tsquery")),
                () -> assertFalse(store.contains("SearchRequest")),
                () -> assertFalse(store.contains("FilterExpressionTextParser")),
                () -> assertFalse(store.contains("similaritySearch(")));
    }

    @Test
    void lexicalAdapterIsTheOnlyOwnerOfFtsRecallSqlAndCodec() throws IOException {
        String store = read(STORE);
        String adapter = read(LEXICAL_ADAPTER);

        assertAll(
                () -> assertTrue(adapter.contains("implements SemanticLexicalRecallPort")),
                () -> assertTrue(adapter.contains("ObjectProvider<JdbcTemplate>")),
                () -> assertTrue(adapter.contains("websearch_to_tsquery")),
                () -> assertTrue(adapter.contains("ts_rank_cd")),
                () -> assertTrue(adapter.contains("parseMetadata(")),
                () -> assertTrue(adapter.contains("JSON.parseObject(")),
                () -> assertTrue(adapter.contains("SemanticLexicalQueryPolicy")),
                () -> assertTrue(adapter.contains("safeTableName(")),
                () -> assertFalse(adapter.contains("VectorStore")),
                () -> assertFalse(adapter.contains("similaritySearch(")),
                () -> assertFalse(adapter.contains("OpsMemoryTextUtils")),
                () -> assertFalse(store.contains("websearch_to_tsquery")),
                () -> assertFalse(store.contains("ts_rank_cd")),
                () -> assertFalse(store.contains("TOKEN_PATTERN")));
    }

    @Test
    void failureAdapterOwnsOneTimeRetrievalDegradationAndCompositionUsesIndependentAdapters() throws IOException {
        String store = read(STORE);
        String failureAdapter = read(FAILURE_ADAPTER);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(failureAdapter.contains("implements SemanticMemoryRetrievalFailurePort")),
                () -> assertTrue(failureAdapter.contains("volatile boolean unavailableLogged")),
                () -> assertTrue(failureAdapter.contains("onDegraded(")),
                () -> assertTrue(failureAdapter.contains("向量记忆召回失败")),
                () -> assertTrue(failureAdapter.contains("PostgreSQL FTS 记忆召回失败")),
                () -> assertTrue(failureAdapter.contains("语义记忆融合失败")),
                () -> assertFalse(store.contains("volatile boolean unavailableLogged")),
                () -> assertFalse(store.contains("implements SemanticMemoryRetrievalFailurePort")),
                () -> assertTrue(configuration.contains("semanticMemoryRetrievalApplicationService(")),
                () -> assertTrue(configuration.contains("OpsSemanticVectorRecallAdapter vectorRecallAdapter")),
                () -> assertTrue(configuration.contains("SemanticLexicalRecallPort lexicalRecallAdapter")),
                () -> assertTrue(configuration.contains("OpsSemanticRetrievalFailureAdapter failureAdapter")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
