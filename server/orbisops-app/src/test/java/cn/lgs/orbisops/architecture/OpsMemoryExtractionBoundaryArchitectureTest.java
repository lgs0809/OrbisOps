package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMemoryExtractionBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/ops/";

    @Test
    void extractorUsesTypedPolicyAndPreservesHistoricalConstructors() throws IOException {
        String extractor = read(RUNTIME + "OpsMemoryExtractor.java");
        String settings = read(RUNTIME + "OpsMemoryExtractionSettings.java");
        String configuration = read(APPLICATION + "OpsMemoryExtractionConfiguration.java");

        assertAll(
                () -> assertTrue(extractor.contains("OpsMemoryExtractionSettings settings")),
                () -> assertTrue(extractor.contains("public OpsMemoryExtractor()")),
                () -> assertTrue(extractor.contains("public OpsMemoryExtractor(MemoryExtractionApplicationService")),
                () -> assertTrue(extractor.contains("settings.maxItemsPerMessage()")),
                () -> assertTrue(extractor.contains("settings.modelMaxInputChars()")),
                () -> assertTrue(extractor.contains("@Autowired")),
                () -> assertFalse(extractor.contains("@Value")),
                () -> assertFalse(extractor.contains("private boolean enabled")),
                () -> assertFalse(extractor.contains("private int maxItemsPerMessage")),
                () -> assertTrue(extractor.lines().count() <= 75),
                () -> assertTrue(settings.contains("public record OpsMemoryExtractionSettings(")),
                () -> assertTrue(settings.contains("legacyConstructorDefaults()")),
                () -> assertTrue(configuration.contains("orbisops.chat.memory.extraction-enabled")),
                () -> assertTrue(configuration.contains("orbisops.chat.memory.extraction-max-items-per-message")),
                () -> assertTrue(configuration.contains("orbisops.chat.memory.llm-extraction-max-input-chars")));
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
