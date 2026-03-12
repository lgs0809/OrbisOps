package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticMemoryFusionArchitectureTest {

    private static final String DOCUMENT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/model/SemanticMemoryDocumentSnapshot.java";
    private static final String CANDIDATE = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/model/SemanticMemoryRankedCandidate.java";
    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/service/SemanticMemoryFusionPolicy.java";
    private static final String STORE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsPgVectorSemanticMemoryStore.java";
    private static final String VECTOR_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsSemanticVectorRecallAdapter.java";
    private static final String LEXICAL_ADAPTER = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/OpsSemanticLexicalRecallAdapter.java";

    @Test
    void domainOwnsTypedCandidatesOrderedGroupingRrfMergeFinalRankingAndLimit() throws IOException {
        String document = read(DOCUMENT);
        String candidate = read(CANDIDATE);
        String policy = read(POLICY);

        assertAll(
                () -> assertTrue(document.contains("record SemanticMemoryDocumentSnapshot")),
                () -> assertTrue(document.contains("Collections.unmodifiableMap")),
                () -> assertTrue(candidate.contains("record SemanticMemoryRankedCandidate")),
                () -> assertTrue(candidate.contains("rank = Math.max(1, rank)")),
                () -> assertTrue(policy.contains("Collectors.groupingBy(")),
                () -> assertTrue(policy.contains("LinkedHashMap::new")),
                () -> assertTrue(policy.contains("semanticPolicy.documentKey(")),
                () -> assertTrue(policy.contains("metadata.put(\"memory_rrf_score\"")),
                () -> assertTrue(policy.contains("memory_retrieval_sources")),
                () -> assertTrue(policy.contains("memory_vector_rank")),
                () -> assertTrue(policy.contains("memory_lexical_rank")),
                () -> assertTrue(policy.contains("semanticPolicy.turnIndex(")),
                () -> assertTrue(policy.contains("semanticPolicy.score(")),
                () -> assertTrue(policy.contains(".limit(Math.max(1, limit))")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("VectorStore")),
                () -> assertFalse(policy.contains("JdbcTemplate")),
                () -> assertFalse(policy.contains("org.springframework.ai.document.Document")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void recallAdaptersMapInfrastructureDocumentsToTypedCandidatesAndStoreOnlyMapsFinalResults() throws IOException {
        String store = read(STORE);
        String vectorAdapter = read(VECTOR_ADAPTER);
        String lexicalAdapter = read(LEXICAL_ADAPTER);

        assertAll(
                () -> assertTrue(store.contains("applicationFacade.search(memoryMapper.retrievalQuery(")),
                () -> assertFalse(store.contains("retrievalService.search(")),
                () -> assertTrue(store.contains("memoryMapper.messageViews(")),
                () -> assertFalse(store.contains(".map(this::document)")),
                () -> assertFalse(store.contains("SemanticMemoryRankedCandidate")),
                () -> assertFalse(store.contains("new SemanticMemoryRankedCandidate(")),
                () -> assertFalse(store.contains("SemanticMemoryFusionPolicy")),
                () -> assertFalse(store.contains("snapshot(new Document(")),
                () -> assertFalse(store.contains("similaritySearch(")),
                () -> assertFalse(store.contains("websearch_to_tsquery")),
                () -> assertTrue(vectorAdapter.contains("SemanticMemoryRankedCandidate")),
                () -> assertTrue(vectorAdapter.contains("new SemanticMemoryRankedCandidate(")),
                () -> assertTrue(vectorAdapter.contains("similaritySearch(")),
                () -> assertTrue(lexicalAdapter.contains("SemanticMemoryRankedCandidate")),
                () -> assertTrue(lexicalAdapter.contains("new SemanticMemoryRankedCandidate(")),
                () -> assertTrue(lexicalAdapter.contains("websearch_to_tsquery")),
                () -> assertFalse(vectorAdapter.contains("Collectors.groupingBy(")),
                () -> assertFalse(lexicalAdapter.contains("Collectors.groupingBy(")),
                () -> assertFalse(vectorAdapter.contains("memory_retrieval_sources")),
                () -> assertFalse(lexicalAdapter.contains("memory_retrieval_sources")));
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
