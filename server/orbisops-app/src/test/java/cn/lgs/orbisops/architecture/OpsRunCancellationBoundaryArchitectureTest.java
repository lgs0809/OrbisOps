package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsRunCancellationBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void registryDelegatesRetentionAndConcurrentState() throws IOException {
        String registry = read(OPS + "OpsRunCancellationRegistry.java");
        String state = read(OPS + "OpsRunCancellationState.java");
        String settings = read(OPS + "OpsRunCancellationSettings.java");
        String configuration = read(APPLICATION + "OpsRunCancellationConfiguration.java");

        assertAll(
                () -> assertTrue(registry.contains("OpsRunCancellationState state")),
                () -> assertTrue(registry.contains("Thread.currentThread().isInterrupted()")),
                () -> assertTrue(registry.contains("legacyConstructorDefaults()")),
                () -> assertTrue(registry.contains("@Autowired")),
                () -> assertFalse(registry.contains("@Value")),
                () -> assertFalse(registry.contains("ConcurrentHashMap")),
                () -> assertFalse(registry.contains("canceledFinishedAt")),
                () -> assertFalse(registry.contains("cleanupExpired(")),
                () -> assertTrue(registry.lines().count() <= 70),
                () -> assertTrue(state.contains("ConcurrentHashMap.newKeySet()")),
                () -> assertTrue(state.contains("settings.retentionMillis()")),
                () -> assertTrue(state.contains("cleanupExpired()")),
                () -> assertFalse(state.contains("@Service")),
                () -> assertTrue(settings.contains("public record OpsRunCancellationSettings(")),
                () -> assertTrue(configuration.contains("orbisops.runs.cancellation-retention-seconds")));
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
