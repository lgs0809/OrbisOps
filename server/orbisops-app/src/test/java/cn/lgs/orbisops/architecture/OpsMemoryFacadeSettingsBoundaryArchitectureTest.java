package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMemoryFacadeSettingsBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void memoryFacadeUsesOneImmutableRuntimePolicy() throws IOException {
        String facade = read(RUNTIME + "OpsMemoryFacade.java");
        String settings = read(RUNTIME + "OpsMemoryFacadeSettings.java");
        String configuration = read(APPLICATION + "OpsMemoryFacadeConfiguration.java");

        assertAll(
                () -> assertTrue(facade.contains("OpsMemoryFacadeSettings settings")),
                () -> assertTrue(facade.contains("legacyConstructorDefaults()")),
                () -> assertTrue(facade.contains("settings.itemMatchLimit()")),
                () -> assertTrue(facade.contains("settings.assembleTimeoutMillis()")),
                () -> assertTrue(facade.contains("settings.captureBufferSize()")),
                () -> assertTrue(facade.contains("@Autowired")),
                () -> assertFalse(facade.contains("@Value")),
                () -> assertFalse(facade.contains("private boolean enabled")),
                () -> assertFalse(facade.contains("private int hotMaxMessages")),
                () -> assertFalse(facade.contains("private long assembleTimeoutMillis")),
                () -> assertFalse(facade.contains("private int bufferSize()")),
                () -> assertTrue(facade.lines().count() <= 125),
                () -> assertTrue(settings.contains("public record OpsMemoryFacadeSettings(")),
                () -> assertTrue(settings.contains("captureBufferSize()")),
                () -> assertTrue(configuration.contains("orbisops.chat.memory.enabled")),
                () -> assertTrue(configuration.contains("orbisops.chat.memory.semantic-top-k")),
                () -> assertTrue(configuration.contains("orbisops.chat.memory.recency-half-life-turns")),
                () -> assertTrue(configuration.contains("OpsSemanticMemoryRetrievalSettings.from(settings)")));
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
