package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceRepositoryArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/source/";
    private static final String APPLICATION = "orbisops-application/src/main/java/cn/lgs/orbisops/application/source/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/";

    @Test
    void domainOwnsTypedCatalogModelsPoliciesAndRepositories() throws IOException {
        String source = readFiles(List.of(
                DOMAIN + "model/SourceRepositoryCandidate.java",
                DOMAIN + "model/SourceRepository.java",
                DOMAIN + "model/DeploymentRevisionCandidate.java",
                DOMAIN + "model/DeploymentRevision.java",
                DOMAIN + "model/ProjectServiceCandidate.java",
                DOMAIN + "model/ProjectService.java",
                DOMAIN + "model/SourceFile.java",
                DOMAIN + "model/SourceSearchHit.java",
                DOMAIN + "service/SourceRepositoryPolicy.java",
                DOMAIN + "service/ProjectServicePolicy.java",
                DOMAIN + "adapter/repository/ISourceRepositoryRepository.java",
                DOMAIN + "adapter/repository/IDeploymentRevisionRepository.java",
                DOMAIN + "adapter/repository/IProjectServiceRepository.java"));

        assertAll(
                () -> assertTrue(source.contains("record SourceRepository")),
                () -> assertTrue(source.contains("record DeploymentRevision")),
                () -> assertTrue(source.contains("record ProjectService")),
                () -> assertTrue(source.contains("class SourceRepositoryPolicy")),
                () -> assertTrue(source.contains("class ProjectServicePolicy")),
                () -> assertTrue(source.contains("interface ISourceRepositoryRepository")),
                () -> assertTrue(source.contains("interface IDeploymentRevisionRepository")),
                () -> assertTrue(source.contains("interface IProjectServiceRepository")),
                () -> assertFalse(source.contains("org.springframework")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("com.alibaba.fastjson")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.api.dto")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(source.contains("ai_ops_source_repository")),
                () -> assertFalse(source.contains("ProcessBuilder")));
    }

    @Test
    void applicationOwnsTypedUseCasesPortsTransactionsAndAudit() throws IOException {
        String source = readFiles(List.of(
                APPLICATION + "SourceRepositoryApplicationService.java",
                APPLICATION + "ProjectServiceApplicationService.java",
                APPLICATION + "SourceGitPort.java",
                APPLICATION + "SourceProjectDirectoryPort.java",
                APPLICATION + "SourceExecutionResourcePort.java",
                APPLICATION + "SourceMcpProjectionPort.java",
                APPLICATION + "SourceMcpRuntimePort.java",
                APPLICATION + "SourceTransactionPort.java",
                APPLICATION + "SourceAuditEvent.java",
                APPLICATION + "SourceAuditPort.java"));

        assertAll(
                () -> assertTrue(source.contains("class SourceRepositoryApplicationService")),
                () -> assertTrue(source.contains("class ProjectServiceApplicationService")),
                () -> assertTrue(source.contains("record SourceAuditEvent")),
                () -> assertTrue(source.contains("SourceTransactionPort")),
                () -> assertTrue(source.contains("SourceGitPort")),
                () -> assertFalse(source.contains("OpsSourceRepositoryDTO")),
                () -> assertFalse(source.contains("OpsProjectServiceDTO")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.api.dto")),
                () -> assertFalse(source.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(source.contains("org.springframework")),
                () -> assertFalse(source.contains("JdbcTemplate")),
                () -> assertFalse(source.contains("com.alibaba.fastjson")),
                () -> assertFalse(source.contains("ProcessBuilder")),
                () -> assertFalse(source.contains("ai_ops_source_repository")));
    }

    @Test
    void infrastructureExclusivelyOwnsSqlDdlJsonAndGitProcesses() throws IOException {
        String repository = read(INFRASTRUCTURE + "JdbcSourceCatalogRepository.java");
        String schema = read(INFRASTRUCTURE + "JdbcSourceCatalogSchemaInitializer.java");
        String git = read(INFRASTRUCTURE + "LocalGitSourceGateway.java");

        assertAll(
                () -> assertTrue(repository.contains("implements\n        ISourceRepositoryRepository")),
                () -> assertTrue(repository.contains("IDeploymentRevisionRepository")),
                () -> assertTrue(repository.contains("IProjectServiceRepository")),
                () -> assertTrue(repository.contains("ai_ops_source_repository")),
                () -> assertTrue(repository.contains("ai_ops_deployment_revision")),
                () -> assertTrue(repository.contains("ai_ops_project_service")),
                () -> assertTrue(repository.contains("JSON.toJSONString")),
                () -> assertTrue(repository.contains("ON DUPLICATE KEY UPDATE")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS ai_ops_source_repository")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS ai_ops_deployment_revision")),
                () -> assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS ai_ops_project_service")),
                () -> assertTrue(git.contains("new ProcessBuilder")),
                () -> assertTrue(git.contains("allowed-local-roots")),
                () -> assertFalse(git.contains("OpsSourceRepositoryDTO")));
    }

    @Test
    void triggerFacadesContainOnlyDtoAclAndTypedDelegation() throws IOException {
        String repositories = read(TRIGGER + "ops/source/OpsSourceRepositoryService.java");
        String services = read(TRIGGER + "ops/source/OpsProjectServiceCatalogService.java");
        String controller = read(TRIGGER + "http/admin/OpsSourceRepositoryAdminController.java");
        String mapper = read(TRIGGER + "application/source/OpsSourceMapper.java");

        String facades = repositories + services;
        assertAll(
                () -> assertTrue(repositories.contains("SourceRepositoryApplicationService")),
                () -> assertTrue(services.contains("ProjectServiceApplicationService")),
                () -> assertTrue(controller.contains("OpsSourceRepositoryService")),
                () -> assertTrue(controller.contains("OpsProjectServiceCatalogService")),
                () -> assertTrue(mapper.contains("OpsSourceRepositoryDTO")),
                () -> assertTrue(mapper.contains("SourceRepositoryCandidate")),
                () -> assertFalse(facades.contains("JdbcTemplate")),
                () -> assertFalse(facades.contains("ProcessBuilder")),
                () -> assertFalse(facades.contains("@PostConstruct")),
                () -> assertFalse(facades.contains("com.alibaba.fastjson")),
                () -> assertFalse(facades.contains("ai_ops_source_repository")),
                () -> assertFalse(facades.contains("CREATE TABLE")));
    }

    @Test
    void obsoleteGenericReverseProxyChainIsPhysicallyAbsent() {
        Path root = projectRoot();
        assertAll(
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-application/src/main/java/cn/lgs/orbisops/application/source/SourceControlApplicationService.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-application/src/main/java/cn/lgs/orbisops/application/source/SourceRepositoryPort.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-application/src/main/java/cn/lgs/orbisops/application/source/ProjectServiceCatalogPort.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/source/OpsSourceRepositoryAdapter.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/source/OpsProjectServiceCatalogAdapter.java"))),
                () -> assertFalse(Files.exists(root.resolve(
                        "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/source/service/SourceControlPolicy.java"))));
    }

    private String readFiles(List<String> paths) throws IOException {
        StringBuilder result = new StringBuilder();
        for (String path : paths) result.append(read(path)).append('\n');
        return result.toString();
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
