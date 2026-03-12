package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCompressionApplicationArchitectureTest {

    private static final String APPLICATION_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryCompressionApplicationService.java";
    private static final String COMMAND = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryCompressionCommand.java";
    private static final String MODEL_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryModelSummaryPort.java";
    private static final String HOT_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/HotMemoryReplacePort.java";
    private static final String MODEL_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryModelSummaryAdapter.java";
    private static final String COMPRESSOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsContextCompressor.java";
    private static final String POST_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryPostProcessingAdapter.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationServiceOwnsModelRuleFallbackProjectionAndHotColdEffects() throws IOException {
        String service = read(APPLICATION_SERVICE);
        String command = read(COMMAND);
        String modelPort = read(MODEL_PORT);
        String hotPort = read(HOT_PORT);

        assertAll(
                () -> assertTrue(command.contains("record MemoryCompressionCommand")),
                () -> assertTrue(command.contains("List<ColdMemoryMessageSnapshot> messages")),
                () -> assertTrue(modelPort.contains("interface MemoryModelSummaryPort")),
                () -> assertTrue(hotPort.contains("interface HotMemoryReplacePort")),
                () -> assertTrue(service.contains("MemoryCompressionPolicy")),
                () -> assertTrue(service.contains("MemoryModelSummaryPort")),
                () -> assertTrue(service.contains("HotMemoryReplacePort")),
                () -> assertTrue(service.contains("ColdMemoryStoreApplicationService")),
                () -> assertTrue(service.contains("compressionPolicy.plan(")),
                () -> assertTrue(service.contains("modelSummaryPort.summarize(")),
                () -> assertTrue(service.contains("catch (RuntimeException ignored)")),
                () -> assertTrue(service.contains("compressionPolicy.ruleSummary(")),
                () -> assertTrue(service.contains("compressionPolicy.protectedMessages(")),
                () -> assertFalse(service.contains("hotMemoryReplacePort.replace(")),
                () -> assertTrue(service.contains("conversationRepository.commitSummary(")),
                () -> assertTrue(service.contains("compressionPolicy.trimContext(")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("ChatClient")),
                () -> assertFalse(service.contains("ChatModel")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("OpsMemoryMessage")),
                () -> assertFalse(service.contains("OpsHotMemoryStore")));
    }

    @Test
    void triggerModelAdapterOnlyOwnsSpringAiSummaryProjection() throws IOException {
        String modelAdapter = read(MODEL_ADAPTER);

        assertAll(
                () -> assertTrue(modelAdapter.contains("implements MemoryModelSummaryPort")),
                () -> assertTrue(modelAdapter.contains("ModelAvailabilityPort")),
                () -> assertTrue(modelAdapter.contains("ChatClient.builder(chatModel)")),
                () -> assertTrue(modelAdapter.contains("modelResolver.resolve()")),
                () -> assertTrue(modelAdapter.contains("modelResolver.chatAvailable()")),
                () -> assertTrue(modelAdapter.contains("Math.max(1200, maxInputChars)")),
                () -> assertTrue(modelAdapter.contains("abbreviate(content == null ? \"\" : content.trim(), 1600)")),
                () -> assertTrue(modelAdapter.contains("String transcript(")),
                () -> assertFalse(modelAdapter.contains("MemoryCompressionPolicy")),
                () -> assertFalse(modelAdapter.contains("ColdMemoryStoreApplicationService")),
                () -> assertFalse(modelAdapter.contains("HotMemoryReplacePort")));
    }

    @Test
    void historicalCompressorAndPostAdapterOnlyMapCompatibilityInputs() throws IOException {
        String compressor = read(COMPRESSOR);
        String postAdapter = read(POST_ADAPTER);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(compressor.contains("MemoryCompressionApplicationService")),
                () -> assertTrue(compressor.contains("new MemoryCompressionCommand(")),
                () -> assertTrue(compressor.contains("applicationService.compress(")),
                () -> assertTrue(compressor.contains("applicationService.trimContext(")),
                () -> assertFalse(compressor.contains("MemoryCompressionPolicy")),
                () -> assertFalse(compressor.contains("MemoryModelSummaryPort")),
                () -> assertFalse(compressor.contains("HotMemoryReplacePort")),
                () -> assertFalse(compressor.contains("ColdMemoryStoreApplicationService")),
                () -> assertFalse(compressor.contains("OpsHotMemoryStore")),
                () -> assertFalse(compressor.contains("ChatClient")),
                () -> assertFalse(compressor.contains("ModelAvailabilityPort")),
                () -> assertTrue(postAdapter.contains("HotMemoryQueryPort hotMemoryQueryPort")),
                () -> assertTrue(postAdapter.contains("hotMemoryQueryPort.recent(")),
                () -> assertFalse(postAdapter.contains("OpsHotMemoryStore")),
                () -> assertTrue(postAdapter.contains("contextCompressor.compressIfNeeded(")),
                () -> assertFalse(postAdapter.contains("ColdMemoryStoreApplicationService")),
                () -> assertFalse(postAdapter.contains("coldMemoryStore")),
                () -> assertTrue(configuration.contains("memoryCompressionApplicationService(")),
                () -> assertTrue(configuration.contains("new MemoryCompressionPolicy(new MemoryContentHashPolicy())")),
                () -> assertTrue(configuration.contains("OpsMemoryModelSummaryAdapter")),
                () -> assertTrue(configuration.contains("HotMemoryReplacePort hotMemoryReplacePort")),
                () -> assertFalse(configuration.contains("OpsHotMemoryReplaceAdapter")));
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
