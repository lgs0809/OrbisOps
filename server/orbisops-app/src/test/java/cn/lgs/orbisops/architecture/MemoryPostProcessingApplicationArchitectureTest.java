package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryPostProcessingApplicationArchitectureTest {

    private static final String APPLICATION_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryPostProcessingApplicationService.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsMemoryFacade.java";
    private static final String ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryPostProcessingAdapter.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationServiceOwnsPostProcessingRoutingAndFailureIsolation() throws IOException {
        String service = read(APPLICATION_SERVICE);

        assertAll(
                () -> assertTrue(service.contains("SemanticMemoryWritePort")),
                () -> assertTrue(service.contains("MemoryExtractionPort")),
                () -> assertTrue(service.contains("ColdMemoryStoreApplicationService")),
                () -> assertTrue(service.contains("ContextMemoryWritePort")),
                () -> assertTrue(service.contains("MemoryCompressionPort")),
                () -> assertTrue(service.contains("MemoryPostProcessingFailurePort")),
                () -> assertTrue(service.contains("executor.execute(task)")),
                () -> assertFalse(service.contains("task.run();")),
                () -> assertTrue(service.contains("MEMORY_BACKGROUND_EXECUTOR_UNAVAILABLE")),
                () -> assertTrue(service.contains("post-processing-submit")),
                () -> assertTrue(service.contains("extraction-submit")),
                () -> assertTrue(service.contains("semantic-append")),
                () -> assertTrue(service.contains("context-memory-save")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(service.contains("OpsMemoryMessage")),
                () -> assertFalse(service.contains("OpsMemoryItem")));
    }

    @Test
    void facadeDelegatesCaptureWithoutOwningPostProcessingDetails() throws IOException {
        String facade = read(FACADE);

        assertAll(
                () -> assertTrue(facade.contains("MemoryCaptureApplicationService")),
                () -> assertTrue(facade.contains("captureService.capture(")),
                () -> assertFalse(facade.contains("MemoryPostProcessingApplicationService")),
                () -> assertFalse(facade.contains("MemoryPostProcessingCommand")),
                () -> assertFalse(facade.contains("postProcessingService.submit(")),
                () -> assertFalse(facade.contains("private void extractAndSave(")),
                () -> assertFalse(facade.contains("private void enqueueMemoryPostProcessing(")),
                () -> assertFalse(facade.contains("semanticStore.appendMessage(")),
                () -> assertFalse(facade.contains("compressor.compressIfNeeded(")),
                () -> assertFalse(facade.contains("memoryExecutorProvider")),
                () -> assertFalse(facade.contains("OpsContextMemoryService contextMemoryService")),
                () -> assertFalse(facade.contains("OpsMemoryExtractor extractor")),
                () -> assertFalse(facade.contains("OpsContextCompressor compressor")));
    }

    @Test
    void triggerAdapterOnlyMapsAndDelegatesLegacyRuntimeSources() throws IOException {
        String adapter = read(ADAPTER);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(adapter.contains("implements SemanticMemoryWritePort")),
                () -> assertTrue(adapter.contains("MemoryExtractionPort")),
                () -> assertTrue(adapter.contains("ContextMemoryWritePort")),
                () -> assertTrue(adapter.contains("MemoryCompressionPort")),
                () -> assertTrue(adapter.contains("OpsMemoryRetrievalMapper")),
                () -> assertTrue(adapter.contains("OpsColdMemoryMapper")),
                () -> assertFalse(adapter.contains("ColdMemoryStoreApplicationService")),
                () -> assertFalse(adapter.contains("coldMemoryStore")),
                () -> assertFalse(adapter.contains("Executor")),
                () -> assertFalse(adapter.contains("try {")),
                () -> assertFalse(adapter.contains("catch (")),
                () -> assertTrue(configuration.contains("memoryPostProcessingApplicationService(")),
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
