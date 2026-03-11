package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiClientModelSyncBoundaryArchitectureTest {

    private static final String APPLICATION_CONFIG =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/config/";
    private static final String TRIGGER_CONFIG =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/config/";

    @Test
    void providerModelSyncMustRemainAHexagonalApplicationProcessManager() throws IOException {
        String useCase = read(APPLICATION_CONFIG + "AiClientModelSyncUseCase.java");
        String targetPort = read(APPLICATION_CONFIG + "AiClientModelSyncTargetPort.java");
        String protocolPort = read(APPLICATION_CONFIG + "AiClientModelSyncProtocolPort.java");
        String catalogPort = read(APPLICATION_CONFIG + "AiClientModelSyncCatalogPort.java");
        String auditPort = read(APPLICATION_CONFIG + "AiClientModelSyncAuditPort.java");
        String targetAdapter = read(TRIGGER_CONFIG + "OpsAiClientModelSyncTargetAdapter.java");
        String protocolAdapter = read(TRIGGER_CONFIG + "OpsAiClientModelSyncHttpProtocolAdapter.java");
        String catalogAdapter = read(TRIGGER_CONFIG + "OpsAiClientModelSyncCatalogAdapter.java");
        String auditAdapter = read(TRIGGER_CONFIG + "OpsAiClientModelSyncAuditAdapter.java");
        String configuration = read(TRIGGER_CONFIG + "AiClientModelSyncConfiguration.java");
        String facade = read(TRIGGER_CONFIG + "AiClientModelApplicationService.java");

        assertAll(
                () -> assertTrue(targetPort.contains("interface AiClientModelSyncTargetPort")),
                () -> assertTrue(protocolPort.contains("interface AiClientModelSyncProtocolPort")),
                () -> assertTrue(catalogPort.contains("interface AiClientModelSyncCatalogPort")),
                () -> assertTrue(auditPort.contains("interface AiClientModelSyncAuditPort")),
                () -> assertTrue(useCase.contains("class AiClientModelSyncUseCase")),
                () -> assertTrue(useCase.contains("private static final int MAX_MODELS_PER_SYNC = 200")),
                () -> assertTrue(useCase.contains("targetPort.find(apiId)")),
                () -> assertTrue(useCase.contains("protocolPort.resolveEndpoint(target)")),
                () -> assertTrue(useCase.contains("protocolPort.fetch(target, endpoint)")),
                () -> assertTrue(useCase.contains("catalogPort.findByModelId(modelId)")),
                () -> assertTrue(useCase.contains("auditPort.succeeded(result)")),
                () -> assertTrue(useCase.contains("auditPort.failed(failed)")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("java.net.http")),
                () -> assertFalse(useCase.contains("com.alibaba.fastjson")),
                () -> assertFalse(useCase.contains("Repository")),
                () -> assertFalse(useCase.contains("DTO")),
                () -> assertFalse(useCase.contains("AiClientConfigRecord")),
                () -> assertTrue(targetAdapter.contains("AiClientApiCatalogUseCase")),
                () -> assertFalse(targetAdapter.contains("IAiClientApiConfigRepository")),
                () -> assertFalse(targetAdapter.contains("HttpClient")),
                () -> assertFalse(targetAdapter.contains("OpsConfigAuditService")),
                () -> assertTrue(protocolAdapter.contains("java.net.http.HttpClient")),
                () -> assertTrue(protocolAdapter.contains("com.alibaba.fastjson.JSON")),
                () -> assertFalse(protocolAdapter.contains("IAiClientModelConfigRepository")),
                () -> assertFalse(protocolAdapter.contains("OpsConfigAuditService")),
                () -> assertTrue(protocolAdapter.contains("catch (InterruptedException e)")),
                () -> assertTrue(catalogAdapter.contains("AiClientModelCatalogPort")),
                () -> assertFalse(catalogAdapter.contains("domain.agent")),
                () -> assertFalse(catalogAdapter.contains("AiClientConfigRecord")),
                () -> assertFalse(catalogAdapter.contains("IAiClientApiConfigRepository")),
                () -> assertFalse(catalogAdapter.contains("HttpClient")),
                () -> assertFalse(catalogAdapter.contains("OpsConfigAuditService")),
                () -> assertTrue(auditAdapter.contains("OpsConfigAuditService")),
                () -> assertFalse(auditAdapter.contains("IAiClientModelConfigRepository")),
                () -> assertFalse(auditAdapter.contains("HttpClient")),
                () -> assertTrue(configuration.contains("AiClientModelSyncUseCase aiClientModelSyncUseCase")),
                () -> assertTrue(configuration.contains("new OpsAiClientModelSyncTargetAdapter")),
                () -> assertTrue(configuration.contains("new OpsAiClientModelSyncHttpProtocolAdapter")),
                () -> assertTrue(configuration.contains("new OpsAiClientModelSyncCatalogAdapter")),
                () -> assertTrue(configuration.contains("new OpsAiClientModelSyncAuditAdapter")),
                () -> assertTrue(facade.contains("private final AiClientModelCatalogUseCase catalogUseCase")),
                () -> assertTrue(facade.contains("private final AiClientModelSyncUseCase syncUseCase")),
                () -> assertTrue(facade.contains("return toSyncResponse(syncUseCase.sync(apiId))")),
                () -> assertFalse(facade.contains("IAiClientModelConfigRepository")),
                () -> assertFalse(facade.contains("IAiClientApiConfigRepository")),
                () -> assertFalse(facade.contains("OpsConfigAuditService")),
                () -> assertFalse(facade.contains("HttpClient")),
                () -> assertFalse(facade.contains("java.net.URI")),
                () -> assertFalse(facade.contains("com.alibaba.fastjson")),
                () -> assertFalse(facade.contains("AiClientConfigRecord")),
                () -> assertFalse(facade.contains("LocalDateTime.now")));
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
