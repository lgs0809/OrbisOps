package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticMemoryDomainArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/service/SemanticMemoryPolicy.java";
    private static final String STORE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsPgVectorSemanticMemoryStore.java";
    private static final String VECTOR_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsSemanticVectorRecallAdapter.java";
    private static final String LEXICAL_ADAPTER = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/OpsSemanticLexicalRecallAdapter.java";
    private static final String WRITE_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticMemoryWriteApplicationService.java";

    @Test
    void domainPolicyOwnsMetadataScopeLifecycleRrfRecencyImportanceKindAndStableKey() throws IOException {
        String policy = read(POLICY);

        assertAll(
                () -> assertTrue(policy.contains("baseMetadata(")),
                () -> assertTrue(policy.contains("metadata.put(\"memory_type\", \"ops_chat\")")),
                () -> assertTrue(policy.contains("metadata.put(\"knowledge\", \"ops-chat-memory\")")),
                () -> assertTrue(policy.contains("metadata.put(\"source\", \"ops_memory_facade\")")),
                () -> assertTrue(policy.contains("inScope(")),
                () -> assertTrue(policy.contains("active(")),
                () -> assertTrue(policy.contains("superseded_by")),
                () -> assertTrue(policy.contains("SUPERSEDED")),
                () -> assertTrue(policy.contains("DELETED")),
                () -> assertTrue(policy.contains("DISABLED")),
                () -> assertTrue(policy.contains("1.0D / (60D + Math.max(1, rank))")),
                () -> assertTrue(policy.contains("memory_rrf_score")),
                () -> assertTrue(policy.contains("distance")),
                () -> assertTrue(policy.contains("recencyWeight(")),
                () -> assertTrue(policy.contains("return 0.85D")),
                () -> assertTrue(policy.contains("0.75D + Math.max(0D, Math.min(1D, importance)) * 0.5D")),
                () -> assertTrue(policy.contains("return 1.08D")),
                () -> assertTrue(policy.contains("return 0.95D")),
                () -> assertTrue(policy.contains("documentKey(")),
                () -> assertTrue(policy.contains("source_message_hash")),
                () -> assertTrue(policy.contains("hashPolicy.stableHash")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("ChatClient")),
                () -> assertFalse(policy.contains("VectorStore")),
                () -> assertFalse(policy.contains("JdbcTemplate")),
                () -> assertFalse(policy.contains("Document")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void writeApplicationUsesMetadataPolicyAndRecallAdaptersUseScopeLifecycleAndRrf() throws IOException {
        String store = read(STORE);
        String vectorAdapter = read(VECTOR_ADAPTER);
        String lexicalAdapter = read(LEXICAL_ADAPTER);
        String writeService = read(WRITE_SERVICE);

        assertAll(
                () -> assertFalse(store.contains("SemanticMemoryPolicy")),
                () -> assertFalse(store.contains("semanticPolicy.baseMetadata(")),
                () -> assertTrue(writeService.contains("SemanticMemoryPolicy")),
                () -> assertTrue(writeService.contains("semanticPolicy.baseMetadata(")),
                () -> assertFalse(writeService.contains("semanticPolicy.inScope(")),
                () -> assertTrue(vectorAdapter.contains("semanticPolicy.inScope(")),
                () -> assertTrue(vectorAdapter.contains("semanticPolicy.rrfScore(")),
                () -> assertTrue(lexicalAdapter.contains("semanticPolicy.inScope(")),
                () -> assertTrue(lexicalAdapter.contains("semanticPolicy.active(")),
                () -> assertTrue(lexicalAdapter.contains("semanticPolicy.rrfScore(")),
                () -> assertFalse(store.contains("private Map<String, Object> baseMetadata(")),
                () -> assertFalse(store.contains("private double memoryScore(")),
                () -> assertFalse(store.contains("private double recencyWeight(")),
                () -> assertFalse(store.contains("private double importanceWeight(")),
                () -> assertFalse(store.contains("private double kindWeight(")),
                () -> assertFalse(store.contains("1.0D / (60D + Math.max(1, rank))")),
                () -> assertFalse(store.contains("OpsMemoryTextUtils.stableHash(document.getText())")));
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
