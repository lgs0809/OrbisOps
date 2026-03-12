package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticMemoryApplicationFacadeArchitectureTest {

    private static final String FACADE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticMemoryApplicationFacade.java";
    private static final String MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsSemanticMemoryMapper.java";
    private static final String STORE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsPgVectorSemanticMemoryStore.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationFacadeIsTheSingleTypedBoundaryForWriteSearchAndClear() throws IOException {
        String facade = read(FACADE);

        assertAll(
                () -> assertTrue(facade.contains("SemanticMemoryWriteApplicationService")),
                () -> assertTrue(facade.contains("SemanticMemoryRetrievalApplicationService")),
                () -> assertTrue(facade.contains("SemanticMemoryClearApplicationService")),
                () -> assertTrue(facade.contains("writeService.write(command)")),
                () -> assertTrue(facade.contains("retrievalService.search(query)")),
                () -> assertTrue(facade.contains("clearService.clear(sessionId)")),
                () -> assertFalse(facade.contains("org.springframework")),
                () -> assertFalse(facade.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(facade.contains("VectorStore")),
                () -> assertFalse(facade.contains("JdbcTemplate")));
    }

    @Test
    void triggerMapperOwnsHistoricalCommandQueryAndResultConversion() throws IOException {
        String mapper = read(MAPPER);

        assertAll(
                () -> assertTrue(mapper.contains("SemanticMemoryWriteCommand writeCommand(")),
                () -> assertTrue(mapper.contains("SemanticMemoryRetrievalQuery retrievalQuery(")),
                () -> assertTrue(mapper.contains("List<OpsMemoryMessage> messageViews(")),
                () -> assertTrue(mapper.contains("new SemanticMemoryWriteCommand(")),
                () -> assertTrue(mapper.contains("new SemanticMemoryRetrievalQuery(")),
                () -> assertTrue(mapper.contains("OpsMemoryMessage.builder()")),
                () -> assertFalse(mapper.contains("VectorStore")),
                () -> assertFalse(mapper.contains("JdbcTemplate")),
                () -> assertFalse(mapper.contains("SemanticMemoryPolicy")));
    }

    @Test
    void legacyStoreOnlyGuardsMapsAndDelegatesToOneApplicationFacade() throws IOException {
        String store = read(STORE);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(store.contains("private final SemanticMemoryApplicationFacade applicationFacade")),
                () -> assertTrue(store.contains("private final OpsSemanticMemoryMapper memoryMapper")),
                () -> assertTrue(store.contains("applicationFacade.write(memoryMapper.writeCommand(")),
                () -> assertTrue(store.contains("applicationFacade.search(memoryMapper.retrievalQuery(")),
                () -> assertTrue(store.contains("memoryMapper.messageViews(")),
                () -> assertTrue(store.contains("applicationFacade.clear(sessionId)")),
                () -> assertFalse(store.contains("private final SemanticMemoryWriteApplicationService")),
                () -> assertFalse(store.contains("private final SemanticMemoryRetrievalApplicationService")),
                () -> assertFalse(store.contains("private final SemanticMemoryClearApplicationService")),
                () -> assertFalse(store.contains("new SemanticMemoryWriteCommand(")),
                () -> assertFalse(store.contains("new SemanticMemoryRetrievalQuery(")),
                () -> assertFalse(store.contains("OpsMemoryMessage.builder()")),
                () -> assertFalse(store.contains("SemanticMemoryDocumentSnapshot")),
                () -> assertTrue(configuration.contains("semanticMemoryApplicationFacade(")),
                () -> assertTrue(configuration.contains("new SemanticMemoryApplicationFacade(")));
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
