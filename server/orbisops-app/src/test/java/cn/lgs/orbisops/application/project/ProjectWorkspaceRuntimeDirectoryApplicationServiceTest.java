package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectWorkspaceRuntimeDirectoryApplicationServiceTest {

    @Test
    void loadsTypedSnapshotsAndIgnoresOrphans() {
        ProjectDefinition payment = project("payment");
        ProjectResourceDefinition mysql = resource(
                "payment", "payment-mysql", "mysql", "ENABLED");
        ProjectResourceDefinition orphan = resource(
                "missing", "missing-mysql", "mysql", "ENABLED");
        ProjectMcpDefinition mcp = mcp(
                "payment", "payment-mysql-mcp", "payment-mysql");
        ProjectMcpDefinition orphanMcp = mcp(
                "missing", "missing-mcp", "missing-mysql");

        ProjectWorkspaceRuntimeDirectoryApplicationService service = service(
                List.of(payment),
                List.of(mysql, orphan),
                List.of(mcp, orphanMcp),
                stored -> Map.of("username", "runtime-user", "password", "runtime-secret"),
                error -> { });

        ProjectWorkspaceRuntimeDirectoryLoadResult result = service.loadPersisted();

        assertTrue(result.loaded());
        assertEquals(1, result.projectCount());
        assertEquals(1, service.resources("payment").size());
        assertEquals(1, service.mcps("payment").size());
        assertTrue(service.resources("missing").isEmpty());
        assertTrue(service.mcps("missing").isEmpty());
        ProjectWorkspaceRuntimeResource runtime = service.resolve(
                "payment", "payment-mysql").orElseThrow();
        assertEquals("runtime-user", runtime.credential().get("username"));
        assertEquals("runtime-secret", runtime.credential().get("password"));
    }

    @Test
    void materializesResourceInPlaceAndMovesUpdatedMcpToFront() {
        ProjectWorkspaceRuntimeDirectoryApplicationService service = service(
                List.of(), List.of(), List.of(), stored -> stored, error -> { });
        service.materializeDefinition(project("payment"));
        service.materializeResource(resource(
                "payment", "payment-mysql", "mysql", "PREVIEW"));
        service.materializeResource(resource(
                "payment", "payment-redis", "redis", "READY"));
        service.materializeResource(resource(
                "payment", "payment-mysql", "mysql", "ENABLED"));

        assertEquals(List.of("payment-mysql", "payment-redis"),
                service.resources("payment").stream()
                        .map(ProjectResourceDefinition::resourceId)
                        .toList());
        assertEquals("ENABLED", service.resources("payment").get(0).status());

        service.materializeMcp(mcp(
                "payment", "payment-first", "payment-mysql"));
        service.materializeMcp(mcp(
                "payment", "payment-second", "payment-redis"));
        service.materializeMcp(mcp(
                "payment", "payment-first", "payment-mysql"));

        assertEquals(List.of("payment-first", "payment-second"),
                service.mcps("payment").stream()
                        .map(ProjectMcpDefinition::mcpId)
                        .toList());
    }

    @Test
    void failedReloadClearsPreviousDirectoryAndReportsFailure() {
        AtomicInteger loads = new AtomicInteger();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        ProjectDefinitionSnapshotPort projectPort = () -> {
            if (loads.getAndIncrement() == 0) {
                return List.of(project("payment"));
            }
            throw new IllegalStateException("project store unavailable");
        };
        ProjectWorkspaceRuntimeDirectoryApplicationService service =
                new ProjectWorkspaceRuntimeDirectoryApplicationService(
                        projectPort,
                        List::of,
                        List::of,
                        stored -> stored,
                        failure::set);

        assertTrue(service.loadPersisted().loaded());
        assertTrue(service.projectExists("payment"));

        ProjectWorkspaceRuntimeDirectoryLoadResult failed = service.loadPersisted();

        assertEquals(ProjectWorkspaceRuntimeDirectoryLoadResult.Status.FAILED, failed.status());
        assertFalse(service.projectExists("payment"));
        assertEquals("project store unavailable", failure.get().getMessage());
    }

    @Test
    void emptyReloadClearsPreviousDirectoryWithoutFailure() {
        List<ProjectDefinition> projects = new ArrayList<>();
        projects.add(project("payment"));
        ProjectWorkspaceRuntimeDirectoryApplicationService service = service(
                projects, List.of(), List.of(), stored -> stored, error -> { });

        assertTrue(service.loadPersisted().loaded());
        projects.clear();

        ProjectWorkspaceRuntimeDirectoryLoadResult empty = service.loadPersisted();

        assertEquals(ProjectWorkspaceRuntimeDirectoryLoadResult.Status.EMPTY, empty.status());
        assertTrue(service.projects().isEmpty());
    }

    private ProjectWorkspaceRuntimeDirectoryApplicationService service(
            List<ProjectDefinition> projects,
            List<ProjectResourceDefinition> resources,
            List<ProjectMcpDefinition> mcps,
            ProjectResourceCredentialResolutionPort credentialPort,
            ProjectWorkspaceRuntimeDirectoryFailurePort failurePort) {
        return new ProjectWorkspaceRuntimeDirectoryApplicationService(
                () -> List.copyOf(projects),
                () -> List.copyOf(resources),
                () -> List.copyOf(mcps),
                credentialPort,
                failurePort);
    }

    private ProjectDefinition project(String projectId) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        return new ProjectDefinition(
                projectId,
                projectId,
                "",
                "ops",
                List.of("prod"),
                "",
                "",
                List.of(),
                List.of(),
                true,
                now,
                now);
    }

    private ProjectResourceDefinition resource(
            String projectId,
            String resourceId,
            String type,
            String status) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        return new ProjectResourceDefinition(
                resourceId,
                projectId,
                ProjectResourceType.from(type),
                type,
                resourceId,
                "prod",
                ProjectResourceType.from(type).defaultEndpoint(),
                Map.of("username", "stored-user", "passwordRef", "${env:DB_PASSWORD}"),
                status,
                Map.of(),
                Map.of("readOnly", true),
                now,
                now);
    }

    private ProjectMcpDefinition mcp(
            String projectId,
            String mcpId,
            String resourceId) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        return new ProjectMcpDefinition(
                mcpId,
                mcpId,
                projectId,
                resourceId,
                "mysql",
                "stdio",
                "mysql-readonly",
                Map.of(),
                List.of("SELECT"),
                cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel.LOW,
                true,
                Map.of("readOnly", true),
                30,
                ProjectMcpStatus.ENABLED,
                now,
                now);
    }
}
