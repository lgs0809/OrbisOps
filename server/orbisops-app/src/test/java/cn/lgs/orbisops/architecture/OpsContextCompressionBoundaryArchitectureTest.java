package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsContextCompressionBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void compressorUsesTypedPolicyAndPreservesHistoricalConstructors() throws IOException {
        String compressor = read(RUNTIME + "OpsContextCompressor.java");
        String settings = read(RUNTIME + "OpsContextCompressionSettings.java");
        String configuration = read(APPLICATION + "OpsContextCompressionConfiguration.java");

        assertAll(
                () -> assertTrue(compressor.contains("OpsContextCompressionSettings settings")),
                () -> assertTrue(compressor.contains("public OpsContextCompressor()")),
                () -> assertTrue(compressor.contains("public OpsContextCompressor(MemoryCompressionApplicationService")),
                () -> assertTrue(compressor.contains("@Autowired")),
                () -> assertTrue(compressor.contains("settings.thresholdMessages()")),
                () -> assertTrue(compressor.contains("settings.modelMaxInputChars()")),
                () -> assertFalse(compressor.contains("@Value")),
                () -> assertFalse(compressor.contains("private boolean enabled")),
                () -> assertFalse(compressor.contains("private int thresholdMessages")),
                () -> assertTrue(compressor.lines().count() <= 85),
                () -> assertTrue(settings.contains("public record OpsContextCompressionSettings(")),
                () -> assertTrue(settings.contains("legacyConstructorDefaults()")),
                () -> assertTrue(configuration.contains("orbisops.chat.memory.compression-enabled")),
                () -> assertTrue(configuration.contains("orbisops.chat.memory.compression-hot-threshold-messages")),
                () -> assertTrue(configuration.contains("orbisops.chat.memory.compression-llm-max-input-chars")));
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
