package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiClientApiHealthBoundaryArchitectureTest {

    private static final String APPLICATION_CONFIG =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/config/";
    private static final String TRIGGER_CONFIG =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/config/";
    private static final String INFRASTRUCTURE_REPOSITORY =
            "orbisops-infrastructure/src/main/java/cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void healthWorkflowProtocolOperatorPersistenceAndAuditMustRemainSeparated() throws IOException {
        String useCase = read(APPLICATION_CONFIG + "AiClientApiHealthCheckUseCase.java");
        String targetPort = read(APPLICATION_CONFIG + "AiClientApiHealthTargetPort.java");
        String probePort = read(APPLICATION_CONFIG + "AiClientApiHealthProbePort.java");
        String operatorPort = read(APPLICATION_CONFIG + "AiClientApiHealthOperatorPort.java");
        String recordPort = read(APPLICATION_CONFIG + "AiClientApiHealthRecordPort.java");
        String auditPort = read(APPLICATION_CONFIG + "AiClientApiHealthAuditPort.java");
        String targetAdapter = read(TRIGGER_CONFIG + "OpsAiClientApiHealthTargetAdapter.java");
        String probeAdapter = read(TRIGGER_CONFIG + "OpsAiClientApiHealthHttpProbeAdapter.java");
        String operatorAdapter = read(TRIGGER_CONFIG + "OpsAiClientApiHealthOperatorAdapter.java");
        String recordRepository = read(INFRASTRUCTURE_REPOSITORY + "AiClientApiHealthCheckRepository.java");
        String resultAdapter = read(TRIGGER_CONFIG + "OpsAiClientApiHealthResultAdapter.java");
        String configuration = read(TRIGGER_CONFIG + "AiClientApiHealthConfiguration.java");
        String facade = read(TRIGGER_CONFIG + "AiClientApiApplicationService.java");

        assertAll(
                () -> assertTrue(useCase.contains("class AiClientApiHealthCheckUseCase")),
                () -> assertTrue(useCase.contains("targetPort.find(apiId)")),
                () -> assertTrue(useCase.contains("probePort.probe(target)")),
                () -> assertTrue(useCase.contains("recordPort.save(result)")),
                () -> assertTrue(useCase.contains("auditPort.record(result)")),
                () -> assertTrue(useCase.indexOf("recordPort.save(result)")
                        < useCase.indexOf("auditPort.record(result)")),
                () -> assertTrue(useCase.contains("auditPort.record(missing)")),
                () -> assertFalse(useCase.contains("recordPort.save(missing)")),
                () -> assertFalse(useCase.contains("HttpClient")),
                () -> assertFalse(useCase.contains("RequestContextHolder")),
                () -> assertFalse(useCase.contains("Repository")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertTrue(targetPort.contains("AiClientApiHealthTarget find")),
                () -> assertTrue(probePort.contains("AiClientApiHealthProbeOutcome probe")),
                () -> assertTrue(operatorPort.contains("String currentOperator()")),
                () -> assertTrue(recordPort.contains("void save(AiClientApiHealthCheckResult result)")),
                () -> assertTrue(auditPort.contains("void record(AiClientApiHealthCheckResult result)")),
                () -> assertTrue(targetAdapter.contains("catalogUseCase.findByApiId(apiId)")),
                () -> assertTrue(probeAdapter.contains("implements AiClientApiHealthProbePort")),
                () -> assertTrue(probeAdapter.contains("private final HttpClient httpClient")),
                () -> assertTrue(probeAdapter.contains("Authorization")),
                () -> assertTrue(probeAdapter.contains("/models")),
                () -> assertTrue(operatorAdapter.contains("RequestContextHolder.getRequestAttributes()")),
                () -> assertTrue(recordRepository.contains("implements AiClientApiHealthRecordPort")),
                () -> assertTrue(recordRepository.contains("AiClientApiHealthCheckResult")),
                () -> assertFalse(recordRepository.contains("domain.agent")),
                () -> assertFalse(recordRepository.contains("AiClientApiHealthCheckRecord")),
                () -> assertTrue(resultAdapter.contains("implements AiClientApiHealthAuditPort")),
                () -> assertFalse(resultAdapter.contains("AiClientApiHealthRecordPort")),
                () -> assertFalse(resultAdapter.contains("Repository")),
                () -> assertTrue(resultAdapter.contains("OpsConfigAuditService")),
                () -> assertTrue(configuration.contains("AiClientApiHealthCheckUseCase aiClientApiHealthCheckUseCase")),
                () -> assertTrue(configuration.contains("AiClientApiHealthRecordPort healthRecordPort")),
                () -> assertTrue(configuration.contains("new OpsAiClientApiHealthHttpProbeAdapter")),
                () -> assertTrue(facade.contains("private final AiClientApiHealthCheckUseCase healthCheckUseCase")),
                () -> assertTrue(facade.contains("healthCheckUseCase.check(apiId)")),
                () -> assertFalse(facade.contains("HttpClient")),
                () -> assertFalse(facade.contains("RequestContextHolder")),
                () -> assertFalse(facade.contains("IAiClientApiConfigRepository")),
                () -> assertFalse(facade.contains("IAiClientApiHealthCheckRepository")),
                () -> assertFalse(facade.contains("OpsConfigAuditService")),
                () -> assertFalse(facade.contains("AiClientApiHealthCheckRecord")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-application"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-application"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-application"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
