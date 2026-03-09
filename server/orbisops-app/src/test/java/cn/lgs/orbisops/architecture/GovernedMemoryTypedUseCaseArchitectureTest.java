package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernedMemoryTypedUseCaseArchitectureTest {

    private static final String CAPTURE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/CaptureMemoryUseCase.java";
    private static final String QUERY = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/QueryRuntimeMemoryUseCase.java";
    private static final String VERIFY = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/VerifyProjectFactUseCase.java";
    private static final String SIGNAL_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemorySkillSignalPort.java";
    private static final String LEGACY_PORT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryStorePort.java";
    private static final String LEGACY_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryStoreAdapter.java";
    private static final String SIGNAL_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemorySkillSignalAdapter.java";
    private static final String MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsGovernedMemoryMapper.java";
    private static final String RUNTIME_TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/memory/OpsMemoryRuntimeInjectionService.java";
    private static final String RUNTIME_PROJECTOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/memory/OpsMemoryRuntimeSelectionProjector.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void applicationUseCasesDependOnTypedGovernedMemoryBoundaryWithoutMaps() throws IOException {
        String capture = read(CAPTURE);
        String query = read(QUERY);
        String verify = read(VERIFY);

        assertAll(
                () -> assertTrue(capture.contains("GovernedMemoryApplicationService")),
                () -> assertTrue(capture.contains("GovernedMemoryCreateCommand")),
                () -> assertTrue(capture.contains("CaptureMemoryResult")),
                () -> assertFalse(capture.contains("MemorySkillSignal")),
                () -> assertFalse(capture.contains("MemoryStorePort")),
                () -> assertFalse(capture.contains("Map<String, Object>")),
                () -> assertFalse(capture.contains("LinkedHashMap")),
                () -> assertTrue(query.contains("GovernedMemoryApplicationService")),
                () -> assertTrue(query.contains("List<GovernedMemorySnapshot>")),
                () -> assertTrue(query.contains("new GovernedMemoryRuntimeQuery(")),
                () -> assertFalse(query.contains("MemoryStorePort")),
                () -> assertFalse(query.contains("Map<String, Object>")),
                () -> assertTrue(verify.contains("GovernedMemoryApplicationService")),
                () -> assertTrue(verify.contains("GovernedMemorySnapshot")),
                () -> assertTrue(verify.contains("GovernedMemoryVerifyCommand")),
                () -> assertFalse(verify.contains("MemoryStorePort")),
                () -> assertFalse(verify.contains("Map<String, Object> current")),
                () -> assertFalse(Files.exists(projectRoot().resolve(SIGNAL_PORT))),
                () -> assertFalse(capture.contains("org.springframework")),
                () -> assertFalse(query.contains("org.springframework")),
                () -> assertFalse(verify.contains("org.springframework")),
                () -> assertFalse(capture.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void obsoleteMapStorePortAndReverseAdapterStayPhysicallyRemoved() {
        assertAll(
                () -> assertFalse(Files.exists(projectRoot().resolve(LEGACY_PORT))),
                () -> assertFalse(Files.exists(projectRoot().resolve(LEGACY_ADAPTER))));
    }

    @Test
    void triggerOwnsLegacyMapProjectionWithoutForegroundLearningCoupling() throws IOException {
        String mapper = read(MAPPER);
        String runtimeTrigger = read(RUNTIME_TRIGGER);
        String runtimeProjector = read(RUNTIME_PROJECTOR);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertFalse(Files.exists(projectRoot().resolve(SIGNAL_ADAPTER))),
                () -> assertTrue(mapper.contains("Map<String, Object> captureView(")),
                () -> assertFalse(mapper.contains("skillEvolutionSignal")),
                () -> assertFalse(mapper.contains("MemorySkillSignal")),
                () -> assertTrue(runtimeTrigger.contains("List<GovernedMemorySnapshot> memories")),
                () -> assertTrue(runtimeTrigger.contains("OpsMemoryRuntimeSelectionProjector projector")),
                () -> assertTrue(runtimeTrigger.contains("projector.project(memories)")),
                () -> assertTrue(runtimeProjector.contains("for (GovernedMemorySnapshot memory : memories)")),
                () -> assertFalse(runtimeTrigger.contains("List<Map<String, Object>> memories")),
                () -> assertFalse(runtimeTrigger.contains("memory.get(\"memoryType\")")),
                () -> assertTrue(configuration.contains("new CaptureMemoryUseCase(memoryApplication)")),
                () -> assertFalse(configuration.contains("MemorySkillSignal")),
                () -> assertTrue(configuration.contains("new QueryRuntimeMemoryUseCase(memoryApplication)")),
                () -> assertTrue(configuration.contains("new VerifyProjectFactUseCase(memoryApplication)")),
                () -> assertFalse(configuration.contains("MemoryStorePort")));
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
