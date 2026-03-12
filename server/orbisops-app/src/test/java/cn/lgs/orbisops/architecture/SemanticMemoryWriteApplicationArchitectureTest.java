package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticMemoryWriteApplicationArchitectureTest {

    private static final String SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticMemoryWriteApplicationService.java";
    private static final String COMMAND = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticMemoryWriteCommand.java";
    private static final String VECTOR_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticVectorWritePort.java";
    private static final String LEXICAL_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticLexicalWritePort.java";
    private static final String FAILURE_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticMemoryWriteFailurePort.java";
    private static final String VECTOR_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsSemanticVectorWriteAdapter.java";
    private static final String LEXICAL_ADAPTER = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/OpsSemanticLexicalWriteAdapter.java";
    private static final String FAILURE_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsSemanticRetrievalFailureAdapter.java";
    private static final String STORE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsPgVectorSemanticMemoryStore.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationOwnsProjectionEmbeddingBranchVectorFirstFallbackAndFailureIsolation() throws IOException {
        String service = read(SERVICE);
        String command = read(COMMAND);
        String vectorPort = read(VECTOR_PORT);
        String lexicalPort = read(LEXICAL_PORT);
        String failurePort = read(FAILURE_PORT);

        assertAll(
                () -> assertTrue(command.contains("record SemanticMemoryWriteCommand")),
                () -> assertTrue(command.contains("boolean embeddingAvailable")),
                () -> assertTrue(vectorPort.contains("interface SemanticVectorWritePort")),
                () -> assertTrue(lexicalPort.contains("interface SemanticLexicalWritePort")),
                () -> assertTrue(failurePort.contains("interface SemanticMemoryWriteFailurePort")),
                () -> assertTrue(service.contains("SemanticMemoryPolicy")),
                () -> assertTrue(service.contains("semanticPolicy.baseMetadata(")),
                () -> assertTrue(service.contains("command.embeddingAvailable()")),
                () -> assertTrue(service.contains("vectorWritePort.writeVector(document)")),
                () -> assertTrue(service.contains("observeFailure(\"vector-write\"")),
                () -> assertTrue(service.contains("lexicalWritePort.writeLexical(document)")),
                () -> assertTrue(service.contains("observeFailure(\"lexical-write\"")),
                () -> assertTrue(service.contains("metadata.put(\"origin_metadata\"")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("VectorStore")),
                () -> assertFalse(service.contains("JdbcTemplate")),
                () -> assertFalse(service.contains("org.springframework.ai.document.Document")),
                () -> assertFalse(service.contains("com.alibaba.fastjson")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void adaptersOwnVectorAndLexicalPersistenceAndSharedFailureObserver() throws IOException {
        String vectorAdapter = read(VECTOR_ADAPTER);
        String lexicalAdapter = read(LEXICAL_ADAPTER);
        String failureAdapter = read(FAILURE_ADAPTER);

        assertAll(
                () -> assertTrue(vectorAdapter.contains("implements SemanticVectorWritePort")),
                () -> assertTrue(vectorAdapter.contains("ObjectProvider<VectorStore>")),
                () -> assertTrue(vectorAdapter.contains("vectorStore.add(")),
                () -> assertFalse(vectorAdapter.contains("JdbcTemplate")),
                () -> assertTrue(lexicalAdapter.contains("implements SemanticLexicalWritePort")),
                () -> assertTrue(lexicalAdapter.contains("ObjectProvider<JdbcTemplate>")),
                () -> assertTrue(lexicalAdapter.contains("INSERT INTO")),
                () -> assertTrue(lexicalAdapter.contains("JSON.toJSONString")),
                () -> assertTrue(lexicalAdapter.contains("safeTableName(")),
                () -> assertFalse(lexicalAdapter.contains("VectorStore")),
                () -> assertTrue(failureAdapter.contains("SemanticMemoryWriteFailurePort")),
                () -> assertTrue(failureAdapter.contains("onWriteFailure(")),
                () -> assertTrue(failureAdapter.contains("vector-write")),
                () -> assertTrue(failureAdapter.contains("PostgreSQL FTS-only 语义记忆写入失败")));
    }

    @Test
    void legacyStoreOnlyMapsHistoricalMessageToTypedWriteCommand() throws IOException {
        String store = read(STORE);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(store.contains("SemanticMemoryApplicationFacade")),
                () -> assertTrue(store.contains("OpsSemanticMemoryMapper")),
                () -> assertTrue(store.contains("applicationFacade.write(memoryMapper.writeCommand(")),
                () -> assertFalse(store.contains("private final SemanticMemoryWriteApplicationService")),
                () -> assertFalse(store.contains("new SemanticMemoryWriteCommand(")),
                () -> assertFalse(store.contains("writeService.write(")),
                () -> assertFalse(store.contains("SemanticMemoryPolicy")),
                () -> assertFalse(store.contains("semanticPolicy.baseMetadata(")),
                () -> assertFalse(store.contains("new Document(")),
                () -> assertFalse(store.contains("vectorStore.add(")),
                () -> assertFalse(store.contains("INSERT INTO")),
                () -> assertFalse(store.contains("JSON.toJSONString")),
                () -> assertFalse(store.contains("appendMessageForLexicalRecall(")),
                () -> assertFalse(store.contains("putIfPresent(")),
                () -> assertTrue(configuration.contains("semanticMemoryWriteApplicationService(")),
                () -> assertTrue(configuration.contains("OpsSemanticVectorWriteAdapter")),
                () -> assertTrue(configuration.contains("SemanticLexicalWritePort lexicalWriteAdapter")),
                () -> assertTrue(configuration.contains("SemanticMemoryWriteApplicationService.withDefaultPolicy(")));
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
