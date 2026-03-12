package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemorySessionClearApplicationArchitectureTest {

    private static final String APPLICATION_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemorySessionClearApplicationService.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsMemoryFacade.java";
    private static final String SEMANTIC_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsSemanticMemoryClearAdapter.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationServiceOwnsMultiStoreClearOrderAndFailureIsolation() throws IOException {
        String service = read(APPLICATION_SERVICE);

        assertAll(
                () -> assertTrue(service.contains("HotMemoryClearPort")),
                () -> assertTrue(service.contains("ColdMemoryStoreApplicationService")),
                () -> assertTrue(service.contains("SemanticMemoryClearPort")),
                () -> assertTrue(service.contains("MemoryCaptureApplicationService")),
                () -> assertTrue(service.contains("hot-clear")),
                () -> assertTrue(service.contains("cold-clear")),
                () -> assertTrue(service.contains("semantic-clear")),
                () -> assertTrue(service.contains("capture-state-clear")),
                () -> assertTrue(service.contains("failedOperations.add(operation)")),
                () -> assertTrue(service.contains("MemorySessionClearFailurePort")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.infrastructure")));
    }

    @Test
    void facadeDelegatesClearWithoutHoldingRuntimeStores() throws IOException {
        String facade = read(FACADE);

        assertAll(
                () -> assertTrue(facade.contains("MemorySessionClearApplicationService")),
                () -> assertTrue(facade.contains("clearService.clear(sessionId)")),
                () -> assertFalse(facade.contains("OpsHotMemoryStore hotStore")),
                () -> assertFalse(facade.contains("ColdMemoryStoreApplicationService coldStore")),
                () -> assertFalse(facade.contains("OpsSemanticMemoryStore semanticStore")),
                () -> assertFalse(facade.contains("hotStore.clear(")),
                () -> assertFalse(facade.contains("coldStore.clear(")),
                () -> assertFalse(facade.contains("semanticStore.clear(")),
                () -> assertFalse(facade.contains("captureService.clearSessionState(")));
    }

    @Test
    void productionConfigurationConsumesTypedHotClearPortAndSemanticAdapterStaysNarrow() throws IOException {
        String semanticAdapter = read(SEMANTIC_ADAPTER);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(configuration.contains("HotMemoryClearPort hotMemoryClearPort")),
                () -> assertTrue(semanticAdapter.contains("implements SemanticMemoryClearPort")),
                () -> assertTrue(semanticAdapter.contains("SemanticMemoryClearApplicationService")),
                () -> assertTrue(semanticAdapter.contains("clearService.clear(sessionId)")),
                () -> assertFalse(semanticAdapter.contains("OpsSemanticMemoryStore")),
                () -> assertFalse(semanticAdapter.contains("semanticMemoryStore")),
                () -> assertFalse(semanticAdapter.contains("hotMemoryStore")),
                () -> assertFalse(semanticAdapter.contains("try {")),
                () -> assertTrue(configuration.contains("memorySessionClearApplicationService(")),
                () -> assertFalse(Files.exists(projectRoot().resolve(
                        "orbisops-trigger/src/main/java/"
                                + "cn/lgs/orbisops/trigger/application/memory/OpsHotMemoryClearAdapter.java"))));
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
