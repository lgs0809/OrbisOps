package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiClientApiCatalogBoundaryArchitectureTest {

    private static final String APPLICATION_CONFIG =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/config/";
    private static final String TRIGGER_CONFIG =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/config/";
    private static final String INFRASTRUCTURE_REPOSITORY =
            "orbisops-infrastructure/src/main/java/cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void catalogCrudSecretResolutionFilteringAndAuditMustBelongToTypedApplicationBoundary()
            throws IOException {
        String definition = read(APPLICATION_CONFIG + "AiClientApiDefinition.java");
        String query = read(APPLICATION_CONFIG + "AiClientApiCatalogQuery.java");
        String port = read(APPLICATION_CONFIG + "AiClientApiCatalogPort.java");
        String auditPort = read(APPLICATION_CONFIG + "AiClientApiCatalogAuditPort.java");
        String useCase = read(APPLICATION_CONFIG + "AiClientApiCatalogUseCase.java");
        String repository = read(INFRASTRUCTURE_REPOSITORY + "AiClientApiConfigRepository.java");
        String adapter = read(TRIGGER_CONFIG + "OpsAiClientApiCatalogAdapter.java");
        String configuration = read(TRIGGER_CONFIG + "AiClientApiCatalogConfiguration.java");
        String facade = read(TRIGGER_CONFIG + "AiClientApiApplicationService.java");

        assertAll(
                () -> assertTrue(definition.contains("record AiClientApiDefinition")),
                () -> assertTrue(definition.contains("OPENAI_COMPATIBLE")),
                () -> assertTrue(query.contains("record AiClientApiCatalogQuery")),
                () -> assertTrue(query.contains("pageNum = Math.max(1, pageNum)")),
                () -> assertTrue(port.contains("boolean insert(AiClientApiDefinition definition)")),
                () -> assertTrue(port.contains("AiClientApiDefinition findByApiId")),
                () -> assertTrue(auditPort.contains("void updatedById")),
                () -> assertTrue(auditPort.contains("void deletedByApiId")),
                () -> assertTrue(useCase.contains("LocalDateTime.now(clock)")),
                () -> assertTrue(useCase.contains("catalogPort.insert(resolved)")),
                () -> assertTrue(useCase.contains("auditPort.created(resolved)")),
                () -> assertTrue(useCase.indexOf("catalogPort.insert(resolved)")
                        < useCase.indexOf("auditPort.created(resolved)")),
                () -> assertTrue(useCase.contains("requested.apiKey().contains(MASK_PLACEHOLDER)")),
                () -> assertTrue(useCase.contains("existing.apiKey()")),
                () -> assertTrue(useCase.contains("api.apiId().contains(safe.apiId())")),
                () -> assertTrue(useCase.contains("api.baseUrl().contains(safe.baseUrl())")),
                () -> assertFalse(useCase.contains("AiClientApiRequestDTO")),
                () -> assertFalse(useCase.contains("AiClientConfigRecord")),
                () -> assertFalse(useCase.contains("OpsConfigAuditService")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertTrue(repository.contains("implements AiClientApiCatalogPort")),
                () -> assertTrue(repository.contains("AiClientApiDefinition")),
                () -> assertFalse(repository.contains("domain.agent")),
                () -> assertFalse(repository.contains("AiClientConfigRecord")),
                () -> assertTrue(adapter.contains("implements AiClientApiCatalogAuditPort")),
                () -> assertFalse(adapter.contains("AiClientApiCatalogPort")),
                () -> assertFalse(adapter.contains("Repository")),
                () -> assertTrue(adapter.contains("private final OpsConfigAuditService auditService")),
                () -> assertTrue(adapter.contains("apiKey(hasText(definition.apiKey()) ? MASK")),
                () -> assertTrue(configuration.contains("AiClientApiCatalogUseCase aiClientApiCatalogUseCase")),
                () -> assertTrue(configuration.contains("AiClientApiCatalogPort catalogPort")),
                () -> assertTrue(configuration.contains(
                        "OpsAiClientApiCatalogAdapter opsAiClientApiCatalogAdapter")),
                () -> assertTrue(configuration.contains("new OpsAiClientApiCatalogAdapter")),
                () -> assertTrue(facade.contains("private final AiClientApiCatalogUseCase catalogUseCase")),
                () -> assertTrue(facade.contains("return catalogUseCase.create(toDefinition(request))")),
                () -> assertTrue(facade.contains("return catalogUseCase.updateById(toDefinition(request))")),
                () -> assertTrue(facade.contains("return toResponses(catalogUseCase.query(query))")),
                () -> assertFalse(facade.contains("@Resource")),
                () -> assertFalse(facade.contains("BeanUtils")),
                () -> assertFalse(facade.contains("resolveApiKey")),
                () -> assertFalse(facade.contains("toModel(")),
                () -> assertFalse(facade.contains("aiClientApiDao")),
                () -> assertFalse(facade.contains("\"api-config\"")));
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
