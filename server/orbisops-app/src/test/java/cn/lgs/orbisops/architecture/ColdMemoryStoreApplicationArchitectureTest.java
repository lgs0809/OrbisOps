package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColdMemoryStoreApplicationArchitectureTest {

    private static final String APPLICATION_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/ColdMemoryStoreApplicationService.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsMemoryFacade.java";
    private static final String COMPRESSOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsContextCompressor.java";
    private static final String LEGACY_STORE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsJdbcColdMemoryStore.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationServiceOwnsAvailabilityAndFailureRouting() throws IOException {
        String service = read(APPLICATION_SERVICE);

        assertAll(
                () -> assertTrue(service.contains("IColdMemoryRepository")),
                () -> assertTrue(service.contains("repository.available()")),
                () -> assertTrue(service.contains("ColdMemoryStoreFailurePort")),
                () -> assertTrue(service.contains("storeEnabled.getAsBoolean()")),
                () -> assertTrue(service.contains("observeFailure(\"append-message\"")),
                () -> assertTrue(service.contains("observeFailure(\"list-items\"")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(service.contains("JdbcTemplate")));
    }

    @Test
    void runtimeConsumersDependOnApplicationUseCaseInsteadOfLegacyStore() throws IOException {
        String facade = read(FACADE);
        String compressor = read(COMPRESSOR);
        String legacyStore = read(LEGACY_STORE);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertFalse(facade.contains("ColdMemoryStoreApplicationService")),
                () -> assertFalse(facade.contains("OpsColdMemoryStore coldStore")),
                () -> assertTrue(compressor.contains("MemoryCompressionApplicationService applicationService")),
                () -> assertFalse(compressor.contains("OpsColdMemoryStore coldStore")),
                () -> assertTrue(legacyStore.contains("ColdMemoryStoreApplicationService")),
                () -> assertFalse(legacyStore.contains("IColdMemoryRepository")),
                () -> assertFalse(legacyStore.contains("repository.available")),
                () -> assertFalse(legacyStore.contains("catch (RuntimeException")),
                () -> assertTrue(configuration.contains("coldMemoryStoreApplicationService(")),
                () -> assertTrue(configuration.contains("orbisops.chat.memory.jdbc-enabled")));
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
