package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagOrderCatalogBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/cn/lgs/orbisops/application/rag/";
    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/rag/";
    private static final String INFRASTRUCTURE_REPOSITORY =
            "orbisops-infrastructure/src/main/java/cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void catalogCrudFilteringTimeAndAuditMustBelongToTypedApplicationBoundary() throws IOException {
        String definition = read(APPLICATION + "RagOrderDefinition.java");
        String query = read(APPLICATION + "RagOrderCatalogQuery.java");
        String port = read(APPLICATION + "RagOrderCatalogPort.java");
        String auditPort = read(APPLICATION + "RagOrderAuditPort.java");
        String useCase = read(APPLICATION + "RagOrderCatalogUseCase.java");
        String repository = read(INFRASTRUCTURE_REPOSITORY + "AiClientRagOrderConfigRepository.java");
        String adapter = read(TRIGGER + "OpsRagOrderCatalogAdapter.java");
        String configuration = read(TRIGGER + "OpsRagOrderCatalogConfiguration.java");
        String facade = read(TRIGGER + "AiClientRagOrderApplicationService.java");

        assertAll(
                () -> assertTrue(definition.contains("record RagOrderDefinition")),
                () -> assertTrue(query.contains("record RagOrderCatalogQuery")),
                () -> assertTrue(port.contains("interface RagOrderCatalogPort")),
                () -> assertTrue(auditPort.contains("interface RagOrderAuditPort")),
                () -> assertTrue(useCase.contains("private final Clock clock")),
                () -> assertTrue(useCase.contains("catalogPort.insert(created)")),
                () -> assertTrue(useCase.contains("catalogPort.queryById(update.id())")),
                () -> assertTrue(useCase.contains("auditPort.updated")),
                () -> assertTrue(useCase.contains("auditPort.deleted")),
                () -> assertTrue(useCase.contains("queryList(RagOrderCatalogQuery query)")),
                () -> assertTrue(useCase.contains("contains(order.ragName(), safe.ragName())")),
                () -> assertTrue(useCase.contains("filtered.subList(start, end)")),
                () -> assertFalse(useCase.contains("cn.lgs.orbisops.api.dto")),
                () -> assertFalse(useCase.contains("IAiClientRagOrderConfigRepository")),
                () -> assertFalse(useCase.contains("AiClientConfigRecord")),
                () -> assertFalse(useCase.contains("OpsConfigAuditService")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(useCase.contains("Map<String, Object>")),
                () -> assertTrue(repository.contains("implements RagOrderCatalogPort")),
                () -> assertTrue(repository.contains("RagOrderDefinition")),
                () -> assertFalse(repository.contains("domain.agent")),
                () -> assertFalse(repository.contains("AiClientConfigRecord")),
                () -> assertTrue(adapter.contains("implements RagOrderAuditPort")),
                () -> assertFalse(adapter.contains("RagOrderCatalogPort")),
                () -> assertFalse(adapter.contains("Repository")),
                () -> assertTrue(adapter.contains("OpsConfigAuditService auditService")),
                () -> assertTrue(adapter.contains("auditService.record")),
                () -> assertTrue(configuration.contains("RagOrderCatalogUseCase ragOrderCatalogUseCase")),
                () -> assertTrue(configuration.contains("RagOrderCatalogPort catalogPort")),
                () -> assertTrue(configuration.contains("new OpsRagOrderCatalogAdapter")),
                () -> assertTrue(configuration.contains("Clock.systemDefaultZone()")),
                () -> assertTrue(facade.contains("private final RagOrderCatalogUseCase ragOrderCatalog")),
                () -> assertTrue(facade.contains("ragOrderCatalog.create")),
                () -> assertTrue(facade.contains("ragOrderCatalog.queryList")),
                () -> assertFalse(facade.contains("listKnowledgeBases")),
                () -> assertFalse(facade.contains("IRagKnowledgeRepository")),
                () -> assertFalse(facade.contains("@Resource")),
                () -> assertFalse(facade.contains("IAiClientRagOrderConfigRepository")),
                () -> assertFalse(facade.contains("AiClientConfigRecord")),
                () -> assertFalse(facade.contains("OpsConfigAuditService")),
                () -> assertFalse(facade.contains("BeanUtils")),
                () -> assertFalse(facade.contains("LocalDateTime.now")),
                () -> assertFalse(facade.contains("aiClientRagOrderDao")));
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
