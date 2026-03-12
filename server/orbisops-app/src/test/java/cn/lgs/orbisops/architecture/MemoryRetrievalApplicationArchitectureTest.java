package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRetrievalApplicationArchitectureTest {

    private static final String APPLICATION_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryRetrievalApplicationService.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsMemoryFacade.java";
    private static final String SOURCE_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryRetrievalSourceAdapter.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationServiceOwnsParallelSliceLoadingAndFailureIsolation() throws IOException {
        String service = read(APPLICATION_SERVICE);

        assertAll(
                () -> assertTrue(service.contains("CompletableFuture")),
                () -> assertTrue(service.contains("completeOnTimeout")),
                () -> assertTrue(service.contains("MemoryRetrievalFailurePort")),
                () -> assertTrue(service.contains("HotMemoryQueryPort")),
                () -> assertTrue(service.contains("SemanticMemoryQueryPort")),
                () -> assertTrue(service.contains("ContextMemoryQueryPort")),
                () -> assertTrue(service.contains("ColdMemoryStoreApplicationService")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(service.contains("JdbcTemplate")));
    }

    @Test
    void facadeDelegatesUnifiedQueryWithoutOwningRetrieval() throws IOException {
        String facade = read(FACADE);

        assertAll(
                () -> assertTrue(facade.contains("MemoryQueryApplicationService")),
                () -> assertTrue(facade.contains("queryService.query(")),
                () -> assertFalse(facade.contains("MemoryRetrievalApplicationService")),
                () -> assertFalse(facade.contains("MemoryRetrievalQuery")),
                () -> assertFalse(facade.contains("MemoryRetrievalResult")),
                () -> assertFalse(facade.contains("loadMemoryAsync(")),
                () -> assertFalse(facade.contains("loadMemorySlice(")),
                () -> assertFalse(facade.contains("joinMemory(")),
                () -> assertFalse(facade.contains("CompletableFuture")),
                () -> assertFalse(facade.contains("completeOnTimeout")),
                () -> assertFalse(facade.contains("contextMemoryService.listForScene")),
                () -> assertFalse(facade.contains("semanticStore.searchMessages")));
    }

    @Test
    void triggerAdapterAndConfigurationOwnOnlyCompositionAndMapping() throws IOException {
        String adapter = read(SOURCE_ADAPTER);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertFalse(adapter.contains("HotMemoryQueryPort")),
                () -> assertFalse(adapter.contains("OpsHotMemoryStore")),
                () -> assertTrue(adapter.contains("SemanticMemoryQueryPort")),
                () -> assertTrue(adapter.contains("ContextMemoryQueryPort")),
                () -> assertTrue(adapter.contains("OpsMemoryRetrievalMapper")),
                () -> assertFalse(adapter.contains("CompletableFuture")),
                () -> assertFalse(adapter.contains("completeOnTimeout")),
                () -> assertTrue(configuration.contains("memoryRetrievalApplicationService(")),
                () -> assertTrue(configuration.contains("opsMemoryExecutor")));
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
