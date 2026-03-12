package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpClientCatalogBoundaryArchitectureTest {

    private static final String APPLICATION_CONFIG =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/config/";
    private static final String TRIGGER_CONFIG =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/config/";
    private static final String INFRASTRUCTURE_REPOSITORY =
            "orbisops-infrastructure/src/main/java/cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void mcpClientCatalogMustRemainAHexagonalApplicationProcessManager() throws IOException {
        String useCase = read(APPLICATION_CONFIG + "McpClientCatalogUseCase.java");
        String catalogPort = read(APPLICATION_CONFIG + "McpClientCatalogPort.java");
        String cachePort = read(APPLICATION_CONFIG + "McpClientRuntimeCachePort.java");
        String auditPort = read(APPLICATION_CONFIG + "McpClientAuditPort.java");
        String protectionPort = read(APPLICATION_CONFIG + "McpTransportConfigProtectionPort.java");
        String repository = read(INFRASTRUCTURE_REPOSITORY + "AiClientToolMcpConfigRepository.java");
        String cacheAdapter = read(TRIGGER_CONFIG + "OpsMcpClientRuntimeCacheAdapter.java");
        String auditAdapter = read(TRIGGER_CONFIG + "OpsMcpClientAuditAdapter.java");
        String protectionAdapter = read(TRIGGER_CONFIG + "OpsMcpTransportConfigProtectionAdapter.java");
        String configuration = read(TRIGGER_CONFIG + "McpClientCatalogConfiguration.java");
        String facade = read(TRIGGER_CONFIG + "AiClientToolMcpApplicationService.java");

        assertAll(
                () -> assertTrue(catalogPort.contains("interface McpClientCatalogPort")),
                () -> assertTrue(cachePort.contains("interface McpClientRuntimeCachePort")),
                () -> assertTrue(auditPort.contains("interface McpClientAuditPort")),
                () -> assertTrue(protectionPort.contains("interface McpTransportConfigProtectionPort")),
                () -> assertTrue(useCase.contains("class McpClientCatalogUseCase")),
                () -> assertTrue(useCase.contains("catalogPort.insert(definition)")),
                () -> assertTrue(useCase.contains("runtimeCachePort.invalidateAll()")),
                () -> assertTrue(useCase.contains("auditPort.created(definition)")),
                () -> assertTrue(useCase.contains("protectionPort.resolveIncoming")),
                () -> assertTrue(useCase.contains("protectionPort.protectForRead")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("com.alibaba.fastjson")),
                () -> assertFalse(useCase.contains("IAiClientToolMcpConfigRepository")),
                () -> assertFalse(useCase.contains("AiClientConfigRecord")),
                () -> assertFalse(useCase.contains("OpsMcpToolProvider")),
                () -> assertFalse(useCase.contains("OpsConfigAuditService")),
                () -> assertFalse(useCase.contains("DTO")),
                () -> assertTrue(repository.contains("implements McpClientCatalogPort")),
                () -> assertTrue(repository.contains("McpClientDefinition")),
                () -> assertFalse(repository.contains("domain.agent")),
                () -> assertFalse(repository.contains("AiClientConfigRecord")),
                () -> assertFalse(repository.contains("BeanUtils")),
                () -> assertTrue(cacheAdapter.contains("OpsMcpToolProvider")),
                () -> assertFalse(cacheAdapter.contains("IAiClientToolMcpConfigRepository")),
                () -> assertTrue(auditAdapter.contains("OpsConfigAuditService")),
                () -> assertFalse(auditAdapter.contains("IAiClientToolMcpConfigRepository")),
                () -> assertTrue(protectionAdapter.contains("com.alibaba.fastjson")),
                () -> assertFalse(protectionAdapter.contains("IAiClientToolMcpConfigRepository")),
                () -> assertFalse(protectionAdapter.contains("OpsMcpToolProvider")),
                () -> assertTrue(configuration.contains("McpClientCatalogUseCase mcpClientCatalogUseCase")),
                () -> assertTrue(configuration.contains("ObjectProvider<OpsMcpToolProvider> toolProvider")),
                () -> assertTrue(facade.contains("private final McpClientCatalogUseCase catalogUseCase")),
                () -> assertFalse(facade.contains("IAiClientToolMcpConfigRepository")),
                () -> assertFalse(facade.contains("AiClientConfigRecord")),
                () -> assertFalse(facade.contains("OpsMcpToolProvider")),
                () -> assertFalse(facade.contains("OpsConfigAuditService")),
                () -> assertFalse(facade.contains("com.alibaba.fastjson")),
                () -> assertFalse(facade.contains("BeanUtils")),
                () -> assertFalse(facade.contains("ObjectProvider<")),
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
