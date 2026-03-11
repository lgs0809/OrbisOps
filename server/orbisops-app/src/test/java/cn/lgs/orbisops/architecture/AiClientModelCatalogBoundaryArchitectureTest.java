package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiClientModelCatalogBoundaryArchitectureTest {

    private static final String APPLICATION_CONFIG =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/config/";
    private static final String TRIGGER_CONFIG =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/config/";
    private static final String INFRASTRUCTURE_REPOSITORY =
            "orbisops-infrastructure/src/main/java/cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void modelCatalogBusinessSequenceMustBelongToTypedApplicationBoundary() throws IOException {
        String definition = read(APPLICATION_CONFIG + "AiClientModelDefinition.java");
        String query = read(APPLICATION_CONFIG + "AiClientModelCatalogQuery.java");
        String port = read(APPLICATION_CONFIG + "AiClientModelCatalogPort.java");
        String auditPort = read(APPLICATION_CONFIG + "AiClientModelCatalogAuditPort.java");
        String useCase = read(APPLICATION_CONFIG + "AiClientModelCatalogUseCase.java");
        String repository = read(INFRASTRUCTURE_REPOSITORY + "AiClientModelConfigRepository.java");
        String adapter = read(TRIGGER_CONFIG + "OpsAiClientModelCatalogAdapter.java");
        String configuration = read(TRIGGER_CONFIG + "AiClientModelCatalogConfiguration.java");
        String facade = read(TRIGGER_CONFIG + "AiClientModelApplicationService.java");

        assertAll(
                () -> assertTrue(definition.contains("record AiClientModelDefinition")),
                () -> assertTrue(query.contains("record AiClientModelCatalogQuery")),
                () -> assertTrue(port.contains("interface AiClientModelCatalogPort")),
                () -> assertTrue(auditPort.contains("interface AiClientModelCatalogAuditPort")),
                () -> assertTrue(useCase.contains("class AiClientModelCatalogUseCase")),
                () -> assertTrue(useCase.contains("catalogPort.insert(resolved)")),
                () -> assertTrue(useCase.contains("auditPort.created(resolved)")),
                () -> assertTrue(useCase.indexOf("catalogPort.insert(resolved)")
                        < useCase.indexOf("auditPort.created(resolved)")),
                () -> assertTrue(useCase.contains("if (hasText(safe.modelId()))")),
                () -> assertTrue(useCase.contains("if (hasText(safe.apiId()))")),
                () -> assertTrue(useCase.contains("if (hasText(safe.modelType()))")),
                () -> assertTrue(useCase.contains("Integer.valueOf(1).equals(safe.status())")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("AiClientModelRequestDTO")),
                () -> assertFalse(useCase.contains("AiClientConfigRecord")),
                () -> assertFalse(useCase.contains("IAiClientModelConfigRepository")),
                () -> assertFalse(useCase.contains("OpsConfigAuditService")),
                () -> assertTrue(repository.contains("implements AiClientModelCatalogPort")),
                () -> assertTrue(repository.contains("AiClientModelDefinition")),
                () -> assertFalse(repository.contains("domain.agent")),
                () -> assertFalse(repository.contains("AiClientConfigRecord")),
                () -> assertTrue(adapter.contains("implements AiClientModelCatalogAuditPort")),
                () -> assertFalse(adapter.contains("AiClientModelCatalogPort")),
                () -> assertFalse(adapter.contains("Repository")),
                () -> assertTrue(adapter.contains("OpsConfigAuditService")),
                () -> assertTrue(configuration.contains("AiClientModelCatalogUseCase aiClientModelCatalogUseCase")),
                () -> assertTrue(configuration.contains("AiClientModelCatalogPort catalogPort")),
                () -> assertTrue(configuration.contains("new OpsAiClientModelCatalogAdapter")),
                () -> assertTrue(facade.contains("private final AiClientModelCatalogUseCase catalogUseCase")),
                () -> assertTrue(facade.contains("return catalogUseCase.create(toDefinition(request))")),
                () -> assertTrue(facade.contains("return catalogUseCase.updateById(toDefinition(request))")),
                () -> assertTrue(facade.contains("return catalogUseCase.deleteByModelId(modelId)")),
                () -> assertTrue(facade.contains("return toResponses(catalogUseCase.query(query))")),
                () -> assertFalse(facade.contains("@Resource")),
                () -> assertFalse(facade.contains("BeanUtils")),
                () -> assertFalse(facade.contains("private AiClientConfigRecord toModel")));
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
