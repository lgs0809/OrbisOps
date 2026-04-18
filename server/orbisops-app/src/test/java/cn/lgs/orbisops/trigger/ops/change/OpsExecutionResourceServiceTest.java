package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.execution.ExecutionAuditPort;
import cn.lgs.orbisops.application.execution.ExecutionResourceCommandApplicationService;
import cn.lgs.orbisops.application.execution.ExecutionResourceProjectPort;
import cn.lgs.orbisops.application.execution.ExecutionResourceQueryApplicationService;
import cn.lgs.orbisops.application.execution.ExecutionResourceRuntimeDirectoryApplicationService;
import cn.lgs.orbisops.domain.execution.adapter.repository.IExecutionResourceRepository;
import cn.lgs.orbisops.domain.execution.model.ExecutionResource;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceCapability;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceDraft;
import cn.lgs.orbisops.domain.execution.model.ExecutionSourceResource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsExecutionResourceServiceTest {

    @Test
    void shouldCreateProjectScopedResourceAndExposeTypedProposal() {
        InMemoryRepository repository = new InMemoryRepository();
        Fixture fixture = fixture(repository);

        ExecutionResource saved = fixture.commands.upsert(localDraft(), "admin");

        assertEquals("demo-project-runtime", saved.resourceId());
        assertEquals("local-java-service", saved.adapter().code());
        assertTrue(fixture.queries.find("demo-project", "demo-project-runtime").isPresent());
        ExecutionResourceCapability proposal = fixture.queries.proposalCatalog("demo-project").get(0);
        assertEquals(List.of("ARTIFACT_DEPLOY"), proposal.allowedActions());
        assertEquals(List.of("order-service"), proposal.targetObjects());
    }

    @Test
    void shouldNotPublishRuntimeDirectoryWhenPersistenceFails() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.failSave = true;
        Fixture fixture = fixture(repository);

        assertThrows(IllegalStateException.class,
                () -> fixture.commands.upsert(localDraft(), "admin"));
        assertTrue(fixture.queries.find("demo-project", "demo-project-runtime").isEmpty());
        assertEquals(0, fixture.queries.capabilities().resourceCount());
    }

    @Test
    void shouldValidateControlledMysqlWithoutLeakingCredentialIntoProposal() {
        InMemoryRepository repository = new InMemoryRepository();
        Fixture fixture = fixture(repository);

        ExecutionResource saved = fixture.commands.upsert(mysqlDraft(), "admin");
        ExecutionResourceCapability proposal = fixture.queries.proposalCatalog("demo-project").get(0);

        assertEquals("mysql-controlled", saved.adapter().code());
        assertEquals(List.of("MYSQL_CREATE_INDEX"), proposal.allowedActions());
        assertEquals(List.of("orders"), proposal.targetObjects());
        assertFalse(proposal.constraints().containsKey("passwordFile"));
    }

    @Test
    void shouldRejectWorkerResourceIdentityCollisionAcrossProjects() {
        InMemoryRepository repository = new InMemoryRepository();
        Fixture fixture = fixture(repository);
        fixture.commands.upsert(localDraft(), "admin");
        fixture.projects.projectEnvironments.put("other-project", List.of("prod"));

        ExecutionResourceDraft duplicate = new ExecutionResourceDraft(
                "demo-project-runtime",
                "other-project",
                "duplicate",
                "worker-a",
                "local-java-service",
                "",
                List.of("prod"),
                localConfiguration(),
                "ENABLED");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fixture.commands.upsert(duplicate, "admin"));
        assertTrue(error.getMessage().contains("全局唯一"));
    }

    @Test
    void shouldRejectShellLikeLauncherAndRelativePaths() {
        Fixture fixture = fixture(new InMemoryRepository());
        Map<String, Object> configuration = localConfiguration();
        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) configuration.get("services");
        @SuppressWarnings("unchecked")
        Map<String, Object> service = (Map<String, Object>) services.get("order-service");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> targets = (List<Map<String, Object>>) service.get("targets");
        targets.get(0).put("javaBinary", "/bin/sh");
        targets.get(0).put("artifactTarget", "relative/app.jar");

        ExecutionResourceDraft invalid = new ExecutionResourceDraft(
                "invalid-runtime", "demo-project", "invalid", "worker-a",
                "local-java-service", "", List.of("prod"), configuration, "ENABLED");

        assertThrows(IllegalArgumentException.class,
                () -> fixture.commands.upsert(invalid, "admin"));
    }

    private Fixture fixture(InMemoryRepository repository) {
        ExecutionResourceRuntimeDirectoryApplicationService directory =
                new ExecutionResourceRuntimeDirectoryApplicationService(repository);
        ProjectPort projects = new ProjectPort();
        projects.projectEnvironments.put("demo-project", List.of("prod", "staging"));
        projects.sources.put("demo-project:mysql-prod",
                new ExecutionSourceResource("mysql-prod", "mysql", "prod"));
        ExecutionAuditPort audit = (projectId, module, action, targetId, before, after) -> {
        };
        return new Fixture(
                new ExecutionResourceCommandApplicationService(
                        repository, directory, projects, audit, true),
                new ExecutionResourceQueryApplicationService(directory, true),
                projects);
    }

    private ExecutionResourceDraft localDraft() {
        return new ExecutionResourceDraft(
                "demo-project-runtime",
                "demo-project",
                "Demo Runtime",
                "worker-a",
                "local-java-service",
                "",
                List.of("prod"),
                localConfiguration(),
                "ENABLED");
    }

    private Map<String, Object> localConfiguration() {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("artifactTarget", "/srv/demo-project/app.jar");
        target.put("pidFile", "/srv/demo-project/app.pid");
        target.put("logFile", "/srv/demo-project/app.log");
        target.put("javaBinary", "java");
        target.put("fixedArgs", new ArrayList<>());
        target.put("environment", new LinkedHashMap<>());
        Map<String, Object> service = new LinkedHashMap<>();
        service.put("healthUrl", "http://127.0.0.1:8080/actuator/health");
        service.put("targets", new ArrayList<>(List.of(target)));
        Map<String, Object> services = new LinkedHashMap<>();
        services.put("order-service", service);
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("allowedArtifactRoot", "/srv/artifacts");
        configuration.put("releaseRoot", "/srv/releases");
        configuration.put("services", services);
        return configuration;
    }

    private ExecutionResourceDraft mysqlDraft() {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("sourceResourceId", "mysql-prod");
        configuration.put("endpoint", "mysql://127.0.0.1:3306/demo_db");
        configuration.put("username", "ops_user");
        configuration.put("passwordFile", "/run/secrets/mysql-password");
        configuration.put("idempotencyTable", "ops_change_request");
        configuration.put("allowedObjects", List.of("orders"));
        configuration.put("allowedActions", List.of("MYSQL_CREATE_INDEX"));
        return new ExecutionResourceDraft(
                "mysql-executor", "demo-project", "MySQL Executor", "worker-db",
                "mysql-controlled", "", List.of("prod"), configuration, "ENABLED");
    }

    private record Fixture(
            ExecutionResourceCommandApplicationService commands,
            ExecutionResourceQueryApplicationService queries,
            ProjectPort projects) {
    }

    private static final class ProjectPort implements ExecutionResourceProjectPort {
        private final Map<String, List<String>> projectEnvironments = new LinkedHashMap<>();
        private final Map<String, ExecutionSourceResource> sources = new LinkedHashMap<>();

        @Override
        public boolean exists(String projectId) {
            return projectEnvironments.containsKey(projectId);
        }

        @Override
        public List<String> environments(String projectId) {
            return projectEnvironments.getOrDefault(projectId, List.of());
        }

        @Override
        public Optional<ExecutionSourceResource> findSourceResource(
                String projectId,
                String resourceId) {
            return Optional.ofNullable(sources.get(projectId + ":" + resourceId));
        }
    }

    private static final class InMemoryRepository implements IExecutionResourceRepository {
        private final Map<String, ExecutionResource> data = new LinkedHashMap<>();
        private boolean failSave;

        @Override
        public List<ExecutionResource> findAllVisible() {
            return List.copyOf(data.values());
        }

        @Override
        public Optional<ExecutionResource> find(String projectId, String resourceId) {
            return Optional.ofNullable(data.get(projectId + ":" + resourceId));
        }

        @Override
        public boolean existsWorkerResourceOutsideProject(
                String workerId,
                String resourceId,
                String projectId) {
            return data.values().stream().anyMatch(resource -> workerId.equals(resource.workerId())
                    && resourceId.equals(resource.resourceId())
                    && !projectId.equals(resource.projectId()));
        }

        @Override
        public ExecutionResource save(ExecutionResource resource) {
            if (failSave) throw new IllegalStateException("database unavailable");
            data.put(resource.projectId() + ":" + resource.resourceId(), resource);
            return resource;
        }
    }
}
