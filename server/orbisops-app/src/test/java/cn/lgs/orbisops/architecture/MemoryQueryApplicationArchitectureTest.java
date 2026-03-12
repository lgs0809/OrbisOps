package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryQueryApplicationArchitectureTest {

    private static final String APPLICATION_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryQueryApplicationService.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsMemoryFacade.java";
    private static final String MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryQueryMapper.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationServiceOwnsEndToEndMemoryQueryAssembly() throws IOException {
        String service = read(APPLICATION_SERVICE);

        assertAll(
                () -> assertTrue(service.contains("MemoryRetrievalApplicationService")),
                () -> assertTrue(service.contains("MemorySelectionPolicy")),
                () -> assertTrue(service.contains("MemorySceneClassificationPolicy")),
                () -> assertTrue(service.contains("MemoryContextRenderingApplicationService")),
                () -> assertTrue(service.contains("MemorySelectionReferenceApplicationService")),
                () -> assertTrue(service.contains("MemoryQueryFailurePort")),
                () -> assertTrue(service.contains("new MemoryRetrievalQuery(")),
                () -> assertTrue(service.contains("selectionPolicy.select(")),
                () -> assertTrue(service.contains("renderingService.render(")),
                () -> assertTrue(service.contains("referenceService.assemble(")),
                () -> assertTrue(service.contains("observe(\"query\"")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(service.contains("OpsMemoryItem")),
                () -> assertFalse(service.contains("OpsMemoryMessage")),
                () -> assertFalse(service.contains("OpsMemorySelection")));
    }

    @Test
    void facadeIsOnlyACompatibilityEntryPointForQueryCaptureAndClear() throws IOException {
        String facade = read(FACADE);

        assertAll(
                () -> assertTrue(facade.contains("MemoryQueryApplicationService")),
                () -> assertTrue(facade.contains("OpsMemoryQueryMapper")),
                () -> assertTrue(facade.contains("queryService.query(")),
                () -> assertTrue(facade.contains("captureService.capture(")),
                () -> assertTrue(facade.contains("clearService.clear(")),
                () -> assertFalse(facade.contains("MemoryRetrievalApplicationService")),
                () -> assertFalse(facade.contains("MemoryRetrievalQuery")),
                () -> assertFalse(facade.contains("MemorySelectionPolicy")),
                () -> assertFalse(facade.contains("MemorySceneClassificationPolicy")),
                () -> assertFalse(facade.contains("MemoryContextRenderingApplicationService")),
                () -> assertFalse(facade.contains("MemoryContextRenderingRequest")),
                () -> assertFalse(facade.contains("MemorySelectionReferenceApplicationService")),
                () -> assertFalse(facade.contains("OpsMemorySelectionReferenceMapper")),
                () -> assertFalse(facade.contains("try {")),
                () -> assertFalse(facade.contains("catch (")),
                () -> assertFalse(facade.contains("@Slf4j")),
                () -> assertFalse(facade.contains("log.warn")));
    }

    @Test
    void triggerMapperOwnsMetadataExtractionAndCompatibilityOutput() throws IOException {
        String mapper = read(MAPPER);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(mapper.contains("MemoryQueryCommand command(")),
                () -> assertTrue(mapper.contains("MemoryQueryResult")),
                () -> assertTrue(mapper.contains("OpsMemorySelection selection(")),
                () -> assertTrue(mapper.contains("safeMetadata.get(\"scene\")")),
                () -> assertTrue(mapper.contains("safeMetadata.get(\"taskType\")")),
                () -> assertTrue(mapper.contains("safeMetadata.get(\"projectId\")")),
                () -> assertTrue(mapper.contains("OpsMemorySelectionReferenceMapper")),
                () -> assertFalse(mapper.contains("MemorySelectionPolicy")),
                () -> assertFalse(mapper.contains("MemorySceneClassificationPolicy")),
                () -> assertFalse(mapper.contains("try {")),
                () -> assertTrue(configuration.contains("memoryQueryApplicationService(")),
                () -> assertTrue(configuration.contains("new MemorySelectionPolicy()")),
                () -> assertTrue(configuration.contains("new MemorySceneClassificationPolicy()")));
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
