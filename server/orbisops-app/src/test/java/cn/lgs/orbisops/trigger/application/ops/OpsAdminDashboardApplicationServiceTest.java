package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.changepackage.ChangePackageListQuery;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.worksession.run.WorkSessionRunApplicationService;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunSnapshot;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStatus;
import cn.lgs.orbisops.trigger.ops.OpsResourceHealthService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAdminDashboardApplicationServiceTest {

    @Test
    void attentionOverviewAggregatesOnlyActionableServerOwnedState() {
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        IncidentQueryApplicationService incidents = mock(IncidentQueryApplicationService.class);
        ChangePackageQueryService changes = mock(ChangePackageQueryService.class);
        WorkSessionRunApplicationService workSessions = mock(WorkSessionRunApplicationService.class);
        OpsResourceHealthService health = mock(OpsResourceHealthService.class);
        OpsAdminDashboardApplicationService service = new OpsAdminDashboardApplicationService(
                projects, incidents, changes, workSessions, health);

        when(projects.listEnabled()).thenReturn(List.of(
                Map.of("projectId", "demo-project"),
                Map.of("projectId", "demo-project-2")));
        when(changes.list(any(ChangePackageListQuery.class))).thenReturn(List.of(
                pkg("pkg0", ChangePackageStatus.READY_FOR_REVIEW.name()),
                pkg("pkg1", ChangePackageStatus.REVIEWING.name()),
                pkg("pkg-approved", ChangePackageStatus.APPROVED.name()),
                pkg("pkg2", ChangePackageStatus.LANDING_RUNNING.name()),
                pkg("pkg-validating", ChangePackageStatus.VALIDATING.name()),
                pkg("pkg3", ChangePackageStatus.LANDING_FAILED.name()),
                pkg("pkg4", ChangePackageStatus.VALIDATION_FAILED.name()),
                pkg("pkg-rejected", ChangePackageStatus.REJECTED.name())));
        when(workSessions.listByStatus(WorkSessionRunStatus.WAITING_APPROVAL, 200)).thenReturn(List.of(
                waitingRun("run-1", "demo-project", "Approve controlled rollout", "credential=must-not-leak"),
                waitingRun("run-disabled", "disabled-project", "Hidden decision", "hidden-secret")));
        when(health.snapshot()).thenReturn(Map.of("healthyCount", 4, "degradedCount", 1));

        Map<String, Object> overview = service.overview();

        assertEquals(2L, overview.get("pendingChangeCount"));
        assertEquals(2L, overview.get("runningChangeCount"));
        assertEquals(2L, overview.get("failedChangeCount"));
        assertEquals(1, overview.get("pendingWorkflowDecisionCount"));
        List<?> decisions = (List<?>) overview.get("pendingWorkflowDecisions");
        assertEquals(1, decisions.size());
        Map<?, ?> decision = (Map<?, ?>) decisions.get(0);
        assertEquals("run-1", decision.get("runId"));
        assertEquals("Approve controlled rollout", decision.get("goal"));
        assertFalse(decision.containsKey("requestPayload"));
        assertFalse(decision.toString().contains("must-not-leak"));
        assertFalse(overview.containsKey("agentCount"));
        assertFalse(overview.containsKey("highRiskToolCount"));
        assertFalse(overview.containsKey("auditAlertCount"));
        verify(incidents).list("demo-project", null, 200);
        verify(incidents).list("demo-project-2", null, 200);
        verify(incidents, never()).list("", null, 200);
    }

    @Test
    void safetyCriticalStoreFailureIsNotReportedAsAnEmptyDashboard() {
        ProjectDefinitionApplicationService projects = mock(ProjectDefinitionApplicationService.class);
        IncidentQueryApplicationService incidents = mock(IncidentQueryApplicationService.class);
        ChangePackageQueryService changes = mock(ChangePackageQueryService.class);
        WorkSessionRunApplicationService workSessions = mock(WorkSessionRunApplicationService.class);
        OpsResourceHealthService health = mock(OpsResourceHealthService.class);
        OpsAdminDashboardApplicationService service = new OpsAdminDashboardApplicationService(
                projects, incidents, changes, workSessions, health);
        when(projects.listEnabled()).thenReturn(List.of());
        when(changes.list(any(ChangePackageListQuery.class)))
                .thenThrow(new IllegalStateException("CHANGE_PACKAGE_STORE_UNAVAILABLE"));

        IllegalStateException error = assertThrows(IllegalStateException.class, service::overview);

        assertEquals("CHANGE_PACKAGE_STORE_UNAVAILABLE", error.getMessage());
    }

    private WorkSessionRunSnapshot waitingRun(String runId, String projectId, String query, String hiddenMetadata) {
        Instant now = Instant.parse("2026-08-16T01:00:00Z");
        return new WorkSessionRunSnapshot(
                runId, projectId, "session-1", "user-1", "agent-1", 2, "agent-hash",
                "WORKFLOW", WorkSessionRunStatus.WAITING_APPROVAL, "attempt-1", 3, 1,
                "", "", null, false, Map.of(), "manifest-hash",
                Map.of("query", query, "metadata", Map.of("private", hiddenMetadata)),
                Map.of(), "", now.minusSeconds(60), now);
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
