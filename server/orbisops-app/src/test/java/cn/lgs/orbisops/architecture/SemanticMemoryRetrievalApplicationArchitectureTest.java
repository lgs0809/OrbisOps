package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticMemoryRetrievalApplicationArchitectureTest {

    private static final String SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticMemoryRetrievalApplicationService.java";
    private static final String QUERY = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticMemoryRetrievalQuery.java";
    private static final String VECTOR_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticVectorRecallPort.java";
    private static final String LEXICAL_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticLexicalRecallPort.java";
    private static final String FAILURE_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticMemoryRetrievalFailurePort.java";
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
    void applicationOwnsRecallLimitEmbeddingBranchIndependentFailureIsolationAndFusion() throws IOException {
        String service = read(SERVICE);
        String query = read(QUERY);
        String vectorPort = read(VECTOR_PORT);
        String lexicalPort = read(LEXICAL_PORT);
        String failurePort = read(FAILURE_PORT);

        assertAll(
                () -> assertTrue(query.contains("record SemanticMemoryRetrievalQuery")),
                () -> assertTrue(query.contains("boolean embeddingAvailable")),
                () -> assertTrue(vectorPort.contains("interface SemanticVectorRecallPort")),
                () -> assertTrue(vectorPort.contains("recallVector(")),
                () -> assertTrue(lexicalPort.contains("interface SemanticLexicalRecallPort")),
                () -> assertTrue(lexicalPort.contains("recallLexical(")),
                () -> assertTrue(failurePort.contains("interface SemanticMemoryRetrievalFailurePort")),
                () -> assertTrue(service.contains("Math.max(1, Math.min(Math.max(limit, semanticTopK) * 4, 40))")),
                () -> assertTrue(service.contains("query.embeddingAvailable()")),
                () -> assertTrue(service.contains("recallVector(query, recallLimit)")),
                () -> assertTrue(service.contains("recallLexical(query, recallLimit)")),
                () -> assertTrue(service.contains("observeFailure(\"vector-recall\"")),
                () -> assertTrue(service.contains("observeFailure(\"lexical-recall\"")),
                () -> assertTrue(service.contains("observeFailure(\"fusion\"")),
                () -> assertTrue(service.contains("fusionPolicy.fuseAndRank(")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("VectorStore")),
                () -> assertFalse(service.contains("JdbcTemplate")),
                () -> assertFalse(service.contains("org.springframework.ai.document.Document")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void adaptersOwnVectorFtsAndFailureObservationWhileLegacyStoreOnlyDelegatesSearch() throws IOException {
        String store = read(STORE);
        String vectorAdapter = read(VECTOR_ADAPTER);
        String lexicalAdapter = read(LEXICAL_ADAPTER);
        String failureAdapter = read(FAILURE_ADAPTER);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(store.contains("SemanticMemoryApplicationFacade")),
                () -> assertTrue(store.contains("OpsSemanticMemoryMapper")),
                () -> assertTrue(store.contains("applicationFacade.search(memoryMapper.retrievalQuery(")),
                () -> assertFalse(store.contains("private final SemanticMemoryRetrievalApplicationService")),
                () -> assertFalse(store.contains("new SemanticMemoryRetrievalQuery(")),
                () -> assertFalse(store.contains("retrievalService.search(")),
                () -> assertFalse(store.contains("implements OpsSemanticMemoryStore,")),
                () -> assertFalse(store.contains("SemanticVectorRecallPort")),
                () -> assertFalse(store.contains("SemanticLexicalRecallPort")),
                () -> assertFalse(store.contains("SemanticMemoryRetrievalFailurePort")),
                () -> assertFalse(store.contains("public List<SemanticMemoryRankedCandidate> recallVector(")),
                () -> assertFalse(store.contains("public List<SemanticMemoryRankedCandidate> recallLexical(")),
                () -> assertFalse(store.contains("public void onFailure(")),
                () -> assertFalse(store.contains("similaritySearch(")),
                () -> assertFalse(store.contains("websearch_to_tsquery")),
                () -> assertFalse(store.contains("FilterExpressionTextParser")),
                () -> assertTrue(vectorAdapter.contains("implements SemanticVectorRecallPort")),
                () -> assertTrue(vectorAdapter.contains("similaritySearch(")),
                () -> assertTrue(vectorAdapter.contains("FilterExpressionTextParser")),
                () -> assertTrue(vectorAdapter.contains("new SemanticMemoryRankedCandidate(")),
                () -> assertTrue(lexicalAdapter.contains("implements SemanticLexicalRecallPort")),
                () -> assertTrue(lexicalAdapter.contains("websearch_to_tsquery")),
                () -> assertTrue(lexicalAdapter.contains("new SemanticMemoryRankedCandidate(")),
                () -> assertTrue(failureAdapter.contains("implements SemanticMemoryRetrievalFailurePort")),
                () -> assertTrue(failureAdapter.contains("volatile boolean unavailableLogged")),
                () -> assertTrue(configuration.contains("semanticMemoryRetrievalApplicationService(")),
                () -> assertTrue(configuration.contains("OpsSemanticVectorRecallAdapter")),
                () -> assertTrue(configuration.contains("SemanticLexicalRecallPort")),
                () -> assertTrue(configuration.contains("OpsSemanticRetrievalFailureAdapter")));
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
