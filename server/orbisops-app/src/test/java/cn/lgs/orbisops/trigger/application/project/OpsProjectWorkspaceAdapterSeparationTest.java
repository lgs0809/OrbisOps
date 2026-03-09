package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceType;
import cn.lgs.orbisops.trigger.ops.OpsProjectWorkspaceService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsProjectWorkspaceAdapterSeparationTest {

    @Test
    void queryAdapterOnlyReadsWorkspaceProjection() {
        OpsProjectWorkspaceService service = mock(OpsProjectWorkspaceService.class);
        when(service.snapshot()).thenReturn(Map.of("projects", List.of()));
        when(service.templates()).thenReturn(List.of(Map.of("type", "mysql")));
        when(service.projectDetail("project-1")).thenReturn(Map.of(
                "projectId", "project-1"));
        OpsProjectWorkspaceQueryAdapter adapter =
                new OpsProjectWorkspaceQueryAdapter(service);

        assertEquals(Map.of("projects", List.of()), adapter.snapshot());
        assertEquals(List.of(Map.of("type", "mysql")), adapter.templates());
        assertEquals(Map.of("projectId", "project-1"), adapter.detail("project-1"));
    }

    @Test
    void projectionAdapterOnlyMaterializesWorkspaceProjection() {
        OpsProjectWorkspaceService service = mock(OpsProjectWorkspaceService.class);
        OpsProjectWorkspaceProjectionAdapter adapter =
                new OpsProjectWorkspaceProjectionAdapter(service);
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 12, 0);
        ProjectDefinition definition = new ProjectDefinition(
                "project-1", "Project One", "", "alice", List.of("prod"), "",
                "agent-1", List.of(), List.of(), true, now, now);
        ProjectResourceDefinition resource = new ProjectResourceDefinition(
                "resource-1", "project-1", ProjectResourceType.from("mysql"), "MySQL",
                "Orders DB", "prod", "jdbc:mysql://orders", Map.of(), "READY",
                Map.of(), Map.of(), now, now);
        ProjectMcpDefinition mcp = new ProjectMcpDefinition(
                "mcp-1", "Orders MCP", "project-1", "resource-1", "mysql", "stdio", "",
                Map.of(), List.of("SELECT"), ProjectMcpRiskLevel.LOW, true, Map.of(), 15,
                ProjectMcpStatus.ENABLED, now, now);

        adapter.materializeDefinition(definition);
        adapter.materializeResource(resource);
        adapter.materializeMcp(mcp);

        verify(service).materializeProjectDefinition(org.mockito.ArgumentMatchers.argThat(
                value -> "project-1".equals(value.get("projectId"))));
        verify(service).materializeProjectResource(org.mockito.ArgumentMatchers.argThat(
                value -> "resource-1".equals(value.get("resourceId"))));
        verify(service).materializeProjectMcp(org.mockito.ArgumentMatchers.argThat(
                value -> "mcp-1".equals(value.get("mcpId"))));
    }
}
