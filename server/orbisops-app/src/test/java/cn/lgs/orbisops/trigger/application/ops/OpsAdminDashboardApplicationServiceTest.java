package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.application.changepackage.ChangePackageListQuery;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpCatalogApplicationService;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.OpsResourceHealthService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAdminDashboardApplicationServiceTest {

    @Test
    void shouldAggregateDashboardFromRealServiceResults() {
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        ProjectMcpCatalogApplicationService projectMcps =
                mock(ProjectMcpCatalogApplicationService.class);
        OpsAgentDefinitionApplicationService agents = mock(OpsAgentDefinitionApplicationService.class);
        IncidentQueryApplicationService incidents = mock(IncidentQueryApplicationService.class);
        ChangePackageQueryService changes = mock(ChangePackageQueryService.class);
        OpsConfigAuditService audits = mock(OpsConfigAuditService.class);
        OpsResourceHealthService health = mock(OpsResourceHealthService.class);
        OpsAdminDashboardApplicationService service = new OpsAdminDashboardApplicationService(
                projects, projectMcps, agents, incidents, changes, audits, health);

        ProjectMcpDefinition highRiskTool = mock(ProjectMcpDefinition.class);
        ProjectMcpDefinition lowRiskTool = mock(ProjectMcpDefinition.class);
        when(highRiskTool.riskLevel()).thenReturn(cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel.HIGH);
        when(lowRiskTool.riskLevel()).thenReturn(cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel.LOW);
        when(projects.listEnabled()).thenReturn(List.of(
                Map.of("projectId", "demo-project"),
                Map.of("projectId", "demo-project-2")));
        when(projectMcps.list("demo-project")).thenReturn(List.of(highRiskTool));
        when(projectMcps.list("demo-project-2")).thenReturn(List.of(lowRiskTool));
        when(agents.listAgents()).thenReturn(List.of(
                Map.of("agentId", "agent-1"),
                Map.of("agentId", "agent-2")));
        when(changes.list(any(ChangePackageListQuery.class))).thenReturn(List.of(
                pkg("pkg0", ChangePackageStatus.READY_FOR_REVIEW.name()),
                pkg("pkg1", ChangePackageStatus.REVIEWING.name()),
                pkg("pkg-approved", ChangePackageStatus.APPROVED.name()),
                pkg("pkg2", ChangePackageStatus.LANDING_RUNNING.name()),
                pkg("pkg-validating", ChangePackageStatus.VALIDATING.name()),
                pkg("pkg3", ChangePackageStatus.LANDING_FAILED.name()),
                pkg("pkg4", ChangePackageStatus.VALIDATION_FAILED.name()),
                pkg("pkg-rejected", ChangePackageStatus.REJECTED.name())));
        when(audits.search(any())).thenReturn(List.of(Map.of("audit_id", "a1")));
        when(health.snapshot()).thenReturn(Map.of("healthyCount", 4, "degradedCount", 1));

        Map<String, Object> overview = service.overview();

        assertEquals(2, overview.get("projectCount"));
        assertEquals(2, overview.get("agentCount"));
        assertEquals(2L, overview.get("pendingChangeCount"));
        assertEquals(2L, overview.get("runningChangeCount"));
        assertEquals(2L, overview.get("failedChangeCount"));
        assertEquals(1L, overview.get("highRiskToolCount"));
        assertEquals(1, overview.get("auditAlertCount"));
        assertEquals(8, ((List<?>) overview.get("recentChanges")).size());
        verify(incidents).list("demo-project", null, 200);
        verify(incidents).list("demo-project-2", null, 200);
        verify(incidents, never()).list("", null, 200);
    }

    @Test
    void safetyCriticalStoreFailureIsNotReportedAsAnEmptyDashboard() {
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        ProjectMcpCatalogApplicationService projectMcps =
                mock(ProjectMcpCatalogApplicationService.class);
        OpsAgentDefinitionApplicationService agents = mock(OpsAgentDefinitionApplicationService.class);
        IncidentQueryApplicationService incidents = mock(IncidentQueryApplicationService.class);
        ChangePackageQueryService changes = mock(ChangePackageQueryService.class);
        OpsConfigAuditService audits = mock(OpsConfigAuditService.class);
        OpsResourceHealthService health = mock(OpsResourceHealthService.class);
        OpsAdminDashboardApplicationService service = new OpsAdminDashboardApplicationService(
                projects, projectMcps, agents, incidents, changes, audits, health);
        when(projects.listEnabled()).thenReturn(List.of());
        when(agents.listAgents()).thenReturn(List.of());
        when(changes.list(any(ChangePackageListQuery.class)))
                .thenThrow(new IllegalStateException("CHANGE_PACKAGE_STORE_UNAVAILABLE"));

        IllegalStateException error = assertThrows(IllegalStateException.class, service::overview);

        assertEquals("CHANGE_PACKAGE_STORE_UNAVAILABLE", error.getMessage());
    }

    private Map<String, Object> pkg(String packageId, String status) {
        return Map.of(
                "packageId", packageId,
                "projectId", "demo-project",
                "objective", "修复订单异常",
                "status", status,
                "riskLevel", "MEDIUM",
                "updateTime", "2026-06-24 10:00:00");
    }
}
