package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCaptureApplicationArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/service/MemoryCapturePolicy.java";
    private static final String APPLICATION_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryCaptureApplicationService.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsMemoryFacade.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void domainPolicyOwnsCapturedMessageDefaults() throws IOException {
        String policy = read(POLICY);

        assertAll(
                () -> assertTrue(policy.contains("turn_index")),
                () -> assertTrue(policy.contains("created_at_epoch_ms")),
                () -> assertTrue(policy.contains("memory_status")),
                () -> assertTrue(policy.contains("ACTIVE")),
                () -> assertTrue(policy.contains("assistant")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(policy.contains("lombok")));
    }

    @Test
    void applicationServiceOwnsTurnClockAndCaptureWriteOrder() throws IOException {
        String service = read(APPLICATION_SERVICE);

        assertAll(
                () -> assertFalse(service.contains("AtomicLong")),
                () -> assertTrue(service.contains("Clock clock")),
                () -> assertTrue(service.contains("MemoryCapturePolicy")),
                () -> assertTrue(service.contains("HotMemoryWritePort")),
                () -> assertTrue(service.contains("ColdMemoryStoreApplicationService")),
                () -> assertTrue(service.contains("MemoryPostProcessingApplicationService")),
                () -> assertTrue(service.contains("hotMemoryWritePort.append")),
                () -> assertTrue(service.contains("conversationRepository.capture")),
                () -> assertTrue(service.contains("postProcessingService.submit")),
                () -> assertTrue(service.contains("clearSessionState(")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.infrastructure")));
    }

    @Test
    void facadeDelegatesCaptureWithoutOwningMetadataOrCounters() throws IOException {
        String facade = read(FACADE);

        assertAll(
                () -> assertTrue(facade.contains("MemoryCaptureApplicationService")),
                () -> assertTrue(facade.contains("MemoryCaptureCommand")),
                () -> assertTrue(facade.contains("captureService.capture(")),
                () -> assertFalse(facade.contains("captureService.clearSessionState(")),
                () -> assertFalse(facade.contains("sessionTurnCounters")),
                () -> assertFalse(facade.contains("nextTurnIndex(")),
                () -> assertFalse(facade.contains("created_at_epoch_ms")),
                () -> assertFalse(facade.contains("memory_status")),
                () -> assertFalse(facade.contains("LocalDateTime")),
                () -> assertFalse(facade.contains("DateTimeFormatter")),
                () -> assertFalse(facade.contains("OpsMemoryMessage.builder()")),
                () -> assertFalse(facade.contains("hotStore.append(")),
                () -> assertFalse(facade.contains("coldStore.appendMessage(")));
    }

    @Test
    void productionConfigurationConsumesTypedHotMemoryWritePortDirectly() throws IOException {
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(configuration.contains("HotMemoryWritePort hotMemoryWritePort")),
                () -> assertTrue(configuration.contains("memoryCaptureApplicationService(")),
                () -> assertTrue(configuration.contains("new MemoryCapturePolicy()")),
                () -> assertTrue(configuration.contains("Clock.systemDefaultZone()")),
                () -> assertFalse(Files.exists(projectRoot().resolve(
                        "orbisops-trigger/src/main/java/"
                                + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryCaptureAdapter.java"))));
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
