package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityReadinessDependencyArchitectureTest {

    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/capability/";
    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void readinessDerivationMustRemainInApplicationAndRuntimeDependenciesInAdapter() throws IOException {
        String useCase = read(APPLICATION + "CapabilityReadinessUseCase.java");
        String environmentPort = read(APPLICATION + "CapabilityReadinessEnvironmentPort.java");
        String adapter = read(OPS + "OpsCapabilityReadinessEnvironmentAdapter.java");
        String configuration = read(OPS + "OpsCapabilityReadinessConfiguration.java");
        String facade = read(OPS + "OpsCapabilityReadinessService.java");

        assertAll(
                () -> assertTrue(environmentPort.contains("interface CapabilityReadinessEnvironmentPort")),
                () -> assertTrue(useCase.contains("class CapabilityReadinessUseCase")),
                () -> assertFalse(useCase.contains("SANDBOX_VALIDATION_UNAVAILABLE")),
                () -> assertFalse(useCase.contains("SANDBOX_PROVIDER_NOT_PRODUCTION_ELIGIBLE")),
                () -> assertTrue(useCase.contains("APPROVED_LANDING_DISABLED")),
                () -> assertTrue(useCase.contains("LANDING_OPERATION_JOURNAL_UNAVAILABLE")),
                () -> assertTrue(useCase.contains("LANDING_OPERATION_RECOVERY_UNAVAILABLE")),
                () -> assertTrue(useCase.contains("LANDING_TOOL_RUNTIME_UNAVAILABLE")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("OpsLandingOperationExecutor")),
                () -> assertFalse(useCase.contains("OpsLandingOperationRecoveryService")),
                () -> assertFalse(useCase.contains("OpsConfigAuditService")),
                () -> assertTrue(adapter.contains("private final OpsLandingOperationRecoveryService landingRecoveryService")),
                () -> assertFalse(adapter.contains("sandbox")),
                () -> assertTrue(adapter.contains("CapabilityDependencyReadiness toolRuntime = probe(\"toolRuntimeProfile\"")),
                () -> assertTrue(adapter.contains("toolRuntime.up()")),
                () -> assertFalse(adapter.contains("OpsLandingOperationExecutor")),
                () -> assertFalse(adapter.contains("ObjectProvider<")),
                () -> assertTrue(configuration.contains("ObjectProvider<OpsLandingOperationRecoveryService> landingRecoveryProvider")),
                () -> assertTrue(configuration.contains("landingRecoveryProvider.getIfAvailable()")),
                () -> assertTrue(configuration.contains("CapabilityReadinessUseCase capabilityReadinessUseCase")),
                () -> assertTrue(facade.contains("private final CapabilityReadinessUseCase readinessUseCase")),
                () -> assertFalse(facade.contains("OpsLandingOperationExecutor")),
                () -> assertFalse(facade.contains("OpsLandingOperationRecoveryService")),
                () -> assertFalse(facade.contains("OpsConfigAuditService")),
                () -> assertFalse(facade.contains("LocalDateTime.now")),
                () -> assertFalse(facade.contains("@Autowired")),
                () -> assertTrue(facade.lines().count() <= 60));
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
