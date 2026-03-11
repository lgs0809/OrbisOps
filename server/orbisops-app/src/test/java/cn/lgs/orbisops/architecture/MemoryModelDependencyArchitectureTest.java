package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryModelDependencyArchitectureTest {

    private static final String MEMORY = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/";

    @Test
    void memoryAdaptersMustShareConstructorBoundModelResolver() throws IOException {
        String extraction = read(MEMORY + "OpsMemoryModelExtractionAdapter.java");
        String summary = read(MEMORY + "OpsMemoryModelSummaryAdapter.java");
        String resolver = read(MEMORY + "OpsMemoryChatModelResolver.java");

        assertAll(
                () -> assertTrue(extraction.contains("private final OpsMemoryChatModelResolver modelResolver")),
                () -> assertTrue(extraction.contains("ObjectProvider<ModelAvailabilityPort> aiModelAvailabilityProvider")),
                () -> assertTrue(extraction.contains("modelResolver.resolve()")),
                () -> assertTrue(extraction.contains("modelResolver.chatAvailable()")),
                () -> assertFalse(extraction.contains("@Autowired(required = false)")),
                () -> assertFalse(extraction.contains("private ApplicationContext")),
                () -> assertFalse(extraction.contains("private ObjectProvider<ChatModel>")),
                () -> assertFalse(extraction.contains("private ModelAvailabilityPort")),
                () -> assertTrue(summary.contains("private final OpsMemoryChatModelResolver modelResolver")),
                () -> assertTrue(summary.contains("ObjectProvider<ModelAvailabilityPort> aiModelAvailabilityProvider")),
                () -> assertTrue(summary.contains("modelResolver.resolve()")),
                () -> assertTrue(summary.contains("modelResolver.chatAvailable()")),
                () -> assertFalse(summary.contains("@Autowired(required = false)")),
                () -> assertFalse(summary.contains("private ApplicationContext")),
                () -> assertFalse(summary.contains("private ObjectProvider<ChatModel>")),
                () -> assertFalse(summary.contains("private ModelAvailabilityPort")),
                () -> assertTrue(resolver.contains("applicationContext.getBean(\"openAiChatModel\"")),
                () -> assertTrue(resolver.contains("chatModelProvider.getIfUnique()")),
                () -> assertTrue(resolver.contains("aiModelAvailability.isChatAvailable()")),
                () -> assertFalse(resolver.contains("@Component")));
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
