package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticMemoryClearApplicationArchitectureTest {

    private static final String SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticMemoryClearApplicationService.java";
    private static final String PERSISTENCE_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticMemoryClearPersistencePort.java";
    private static final String FAILURE_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/SemanticMemoryClearFailurePort.java";
    private static final String PERSISTENCE_ADAPTER = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/OpsSemanticMemoryClearPersistenceAdapter.java";
    private static final String OUTWARD_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsSemanticMemoryClearAdapter.java";
    private static final String FAILURE_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsSemanticRetrievalFailureAdapter.java";
    private static final String STORE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsPgVectorSemanticMemoryStore.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationOwnsSessionValidationPersistenceFailureAndObserverIsolation() throws IOException {
        String service = read(SERVICE);
        String persistencePort = read(PERSISTENCE_PORT);
        String failurePort = read(FAILURE_PORT);

        assertAll(
                () -> assertTrue(persistencePort.contains("interface SemanticMemoryClearPersistencePort")),
                () -> assertTrue(persistencePort.contains("boolean clearSession(String sessionId)")),
                () -> assertTrue(failurePort.contains("interface SemanticMemoryClearFailurePort")),
                () -> assertTrue(service.contains("persistencePort.clearSession(sessionId.trim())")),
                () -> assertTrue(service.contains("catch (RuntimeException error)")),
                () -> assertTrue(service.contains("failurePort.onClearFailure(error)")),
                () -> assertTrue(service.contains("catch (RuntimeException ignored)")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("JdbcTemplate")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void persistenceAdapterOwnsJdbcSessionCleanupAndTableSafety() throws IOException {
        String adapter = read(PERSISTENCE_ADAPTER);
        String store = read(STORE);

        assertAll(
                () -> assertTrue(adapter.contains("implements SemanticMemoryClearPersistencePort")),
                () -> assertTrue(adapter.contains("ObjectProvider<JdbcTemplate>")),
                () -> assertTrue(adapter.contains("metadata->>'memory_type' = 'ops_chat'")),
                () -> assertTrue(adapter.contains("metadata->>'session_id' = ?")),
                () -> assertTrue(adapter.contains("safeTableName(")),
                () -> assertFalse(adapter.contains("SemanticMemoryClearApplicationService")),
                () -> assertFalse(store.contains("private final ObjectProvider<JdbcTemplate>")),
                () -> assertFalse(store.contains("template.update(")),
                () -> assertFalse(store.contains("DELETE FROM")),
                () -> assertFalse(store.contains("safeTableName(")),
                () -> assertFalse(store.contains("@Slf4j")));
    }

    @Test
    void outwardAdapterAndLegacyStoreOnlyDelegateToClearApplicationService() throws IOException {
        String outwardAdapter = read(OUTWARD_ADAPTER);
        String failureAdapter = read(FAILURE_ADAPTER);
        String store = read(STORE);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(outwardAdapter.contains("SemanticMemoryClearApplicationService")),
                () -> assertTrue(outwardAdapter.contains("clearService.clear(sessionId)")),
                () -> assertFalse(outwardAdapter.contains("OpsSemanticMemoryStore")),
                () -> assertFalse(outwardAdapter.contains("semanticMemoryStore")),
                () -> assertTrue(store.contains("SemanticMemoryApplicationFacade")),
                () -> assertTrue(store.contains("applicationFacade.clear(sessionId)")),
                () -> assertFalse(store.contains("private final SemanticMemoryClearApplicationService")),
                () -> assertFalse(store.contains("clearService.clear(sessionId)")),
                () -> assertFalse(store.contains("private final OpsSemanticRetrievalFailureAdapter")),
                () -> assertTrue(failureAdapter.contains("SemanticMemoryClearFailurePort")),
                () -> assertTrue(failureAdapter.contains("onClearFailure(")),
                () -> assertTrue(configuration.contains("semanticMemoryClearApplicationService(")),
                () -> assertTrue(configuration.contains("SemanticMemoryClearPersistencePort persistenceAdapter")),
                () -> assertTrue(configuration.contains("new SemanticMemoryClearApplicationService(")));
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
