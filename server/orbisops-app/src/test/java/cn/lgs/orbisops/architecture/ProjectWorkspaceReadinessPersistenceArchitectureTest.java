package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectWorkspaceReadinessPersistenceArchitectureTest {

    private static final String DOMAIN_REPOSITORY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/project/adapter/repository/IProjectWorkspaceReadinessRepository.java";
    private static final String INFRASTRUCTURE_REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcProjectWorkspaceReadinessRepository.java";
    private static final String APPLICATION_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/project/ProjectWorkspaceProjectionApplicationService.java";
    private static final String TRIGGER_SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/OpsProjectWorkspaceService.java";

    @Test
    void domainOwnsNarrowReadinessRepositoryContractWithoutFrameworkLeakage() throws IOException {
        String repository = read(DOMAIN_REPOSITORY);

        assertAll(
                () -> assertTrue(repository.contains("interface IProjectWorkspaceReadinessRepository")),
                () -> assertTrue(repository.contains("boolean available()")),
                () -> assertTrue(repository.contains("countReadySourceRepositories(String projectId)")),
                () -> assertTrue(repository.contains("countEnabledExecutionResources(String projectId)")),
                () -> assertFalse(repository.contains("org.springframework")),
                () -> assertFalse(repository.contains("JdbcTemplate")),
                () -> assertFalse(repository.contains("SELECT COUNT")),
                () -> assertFalse(repository.contains("Map<String, Object>")));
    }

    @Test
    void infrastructureOwnsJdbcConfigurationAndReadinessSql() throws IOException {
        String repository = read(INFRASTRUCTURE_REPOSITORY);

        assertAll(
                () -> assertTrue(repository.contains("implements IProjectWorkspaceReadinessRepository")),
                () -> assertTrue(repository.contains("JdbcTemplate")),
                () -> assertTrue(repository.contains("orbisops.project-workspace.jdbc-enabled")),
                () -> assertTrue(repository.contains("FROM ai_ops_source_repository")),
                () -> assertTrue(repository.contains("status IN ('READY', 'ENABLED')")),
                () -> assertTrue(repository.contains("FROM ai_ops_execution_resource")),
                () -> assertTrue(repository.contains("status = 'ENABLED'")),
                () -> assertTrue(repository.contains("Math.max(count, 0)")));
    }

    @Test
    void applicationOwnsFailOpenReadinessAndTriggerOnlyUsesProjectionBoundary() throws IOException {
        String application = read(APPLICATION_SERVICE);
        String trigger = read(TRIGGER_SERVICE);

        assertAll(
                () -> assertTrue(application.contains("IProjectWorkspaceReadinessRepository")),
                () -> assertTrue(application.contains("readinessRepository.available()")),
                () -> assertTrue(application.contains("countReadySourceRepositories(projectId)")),
                () -> assertTrue(application.contains("countEnabledExecutionResources(projectId)")),
                () -> assertTrue(application.contains("catch (RuntimeException error)")),
                () -> assertTrue(application.contains("failurePort.readinessQueryFailed")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("SELECT COUNT")),
                () -> assertTrue(trigger.contains("ProjectWorkspaceProjectionApplicationService")),
                () -> assertTrue(trigger.contains("runtimeDirectory.loadPersisted()")),
                () -> assertFalse(trigger.contains("workspaceProjectionService.persistenceAvailable()")),
                () -> assertFalse(trigger.contains("IProjectWorkspaceReadinessRepository")),
                () -> assertFalse(trigger.contains("workspaceReadinessRepository")),
                () -> assertFalse(trigger.contains("readinessCount(")),
                () -> assertFalse(trigger.contains("JdbcTemplate")),
                () -> assertFalse(trigger.contains("DataAccessException")),
                () -> assertFalse(trigger.contains("ai_ops_source_repository")),
                () -> assertFalse(trigger.contains("ai_ops_execution_resource")),
                () -> assertFalse(trigger.contains("SELECT COUNT")),
                () -> assertFalse(trigger.contains("jdbcEnabled")),
                () -> assertFalse(trigger.contains("jdbcTemplate()")),
                () -> assertFalse(trigger.contains("connectedResourceCount")));
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
