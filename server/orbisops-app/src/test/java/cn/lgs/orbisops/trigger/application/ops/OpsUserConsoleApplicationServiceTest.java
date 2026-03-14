package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.changepackage.ChangePackageListQuery;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.application.project.ProjectCatalogEntry;
import cn.lgs.orbisops.application.worksession.run.WorkSessionRunApplicationService;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.project.model.ProjectRole;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunSnapshot;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStatus;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackagePermissionService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsUserConsoleApplicationServiceTest {

    @Test
    void shouldReturnAttentionOrientedUserDashboardFromRealServices() {
        Fixture fixture = fixture();
        when(fixture.projectAccessUseCase.catalog("ops_user", "20001", false)).thenReturn(List.of(
                project("demo-project", "示例系统", "demo-ops-agent")));
        when(fixture.projectAccessUseCase.role("demo-project", "ops_user", "20001", false))
                .thenReturn(ProjectRole.APPROVER);
        when(fixture.chatApplicationService.listSessions(any(), any(), any(), any(), anyInt(), eq("20001")))
                .thenReturn(List.of(OpsChatSession.builder()
                        .sessionId("session-1")
                        .userId("20001")
                        .title("排查 5xx")
                        .build()));
        when(fixture.changePackageService.list(any(ChangePackageListQuery.class))).thenReturn(List.of(changePackage()));
        when(fixture.configAuditService.listForOperator(eq("ops_user"), anyInt()))
                .thenReturn(List.of(auditRow()));
        when(fixture.configAuditService.listForOperator(eq("20001"), anyInt()))
                .thenReturn(List.of());
        WorkSessionRunSnapshot waiting = waitingRun("run-1", "demo-project", "Approve database failover", "must-not-leak");
        when(fixture.workSessionRuns.listByStatus(WorkSessionRunStatus.WAITING_APPROVAL, 200)).thenReturn(List.of(waiting));

        Map<String, Object> overview = fixture.service.dashboardOverview("ops_user", "20001");

        assertEquals(1, ((List<?>) overview.get("availableProjects")).size());
        @SuppressWarnings("unchecked")
        Map<String, Object> project = (Map<String, Object>) ((List<?>) overview.get("availableProjects")).get(0);
        assertEquals("APPROVER", project.get("projectRole"));
        assertFalse(overview.containsKey("availableAgents"));
        assertEquals(1, ((List<?>) overview.get("recentSessions")).size());
        assertEquals(1, ((List<?>) overview.get("pendingExecutions")).size());
        assertEquals(1, ((List<?>) overview.get("auditReminders")).size());
        List<?> workflowDecisions = (List<?>) overview.get("pendingWorkflowDecisions");
        assertEquals(1, workflowDecisions.size());
        assertFalse(workflowDecisions.toString().contains("must-not-leak"));
        @SuppressWarnings("unchecked")
        Map<String, Object> queue = (Map<String, Object>) overview.get("workQueue");
        assertEquals(2, queue.get("approvalCount"));
        assertEquals(1, queue.get("workflowDecisionCount"));
    }

    @Test
    void workflowDecisionRequiresCurrentWorkSessionWriteAuthority() {
        Fixture fixture = fixture();
        when(fixture.projectAccessUseCase.catalog("ops_user", "20001", false))
                .thenReturn(List.of(project("demo-project", "示例系统", "")));
        WorkSessionRunSnapshot denied = waitingRun("run-denied", "demo-project", "Viewer cannot approve", "hidden");
        WorkSessionRunSnapshot otherProject = waitingRun("run-other", "other-project", "Outside project", "hidden");
        when(fixture.workSessionRuns.listByStatus(WorkSessionRunStatus.WAITING_APPROVAL, 200))
                .thenReturn(List.of(denied, otherProject));
        doThrow(new SecurityException("WORK_SESSION_APPROVAL_FORBIDDEN"))
                .when(fixture.workSessionRuns).resumeApproval("run-denied", "demo-project", "ops_user");
        doThrow(new SecurityException("WORK_SESSION_APPROVAL_FORBIDDEN"))
                .when(fixture.workSessionRuns).resumeApproval("run-denied", "demo-project", "20001");

        Map<String, Object> overview = fixture.service.dashboardOverview("ops_user", "20001");

        assertTrue(((List<?>) overview.get("pendingWorkflowDecisions")).isEmpty());
    }

    @Test
    void shouldSanitizeUserExecutionAndAuditSummaries() {
        Fixture fixture = fixture();
        when(fixture.projectAccessUseCase.catalog("ops_user", "20001", false))
                .thenReturn(List.of(project("demo-project", "示例系统", "")));
        when(fixture.changePackageService.list(any(ChangePackageListQuery.class))).thenReturn(List.of(changePackage()));
        when(fixture.configAuditService.listForOperator(eq("ops_user"), anyInt()))
                .thenReturn(List.of(auditRow()));
        when(fixture.configAuditService.listForOperator(eq("20001"), anyInt()))
                .thenReturn(List.of());

        Map<String, Object> execution = fixture.service.myExecutions("ops_user", "20001", null, 10).get(0);
        Map<String, Object> audit = fixture.service.myAudits("ops_user", "20001", 10).get(0);

        assertEquals("AUTHORIZED_PROJECT", execution.get("relation"));
        assertEquals(1, execution.get("actionCount"));
        assertFalse(execution.containsKey("actions"));
        assertFalse(execution.containsKey("evidence"));
        assertFalse(audit.containsKey("before_json"));
        assertFalse(audit.containsKey("after_json"));
        assertFalse(audit.containsKey("client_ip"));
    }

    @Test
    void shouldExposePendingApprovalOnlyInsideAuthorizedProject() {
        Fixture fixture = fixture();
        when(fixture.projectAccessUseCase.catalog("ops_user", "20001", false))
                .thenReturn(List.of(project("demo-project", "示例系统", "")));
        when(fixture.changePackageService.list(any(ChangePackageListQuery.class))).thenReturn(List.of(changePackage()));

        Map<String, Object> execution = fixture.service.myExecutions("ops_user", "20001", null, 10).get(0);

        assertEquals("AUTHORIZED_PROJECT", execution.get("relation"));
        assertTrue(Boolean.TRUE.equals(execution.get("canApprove")));
        assertTrue(Boolean.TRUE.equals(execution.get("canReject")));
    }

    private ProjectCatalogEntry project(String projectId, String name, String defaultAgentId) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 15, 40);
        return new ProjectCatalogEntry(
                projectId,
                name,
                "",
                "ops_user",
                List.of("prod"),
                "",
                defaultAgentId,
                List.of(),
                List.of(),
                true,
                now,
                now);
    }

    private Fixture fixture() {
        AuthorizeProjectAccessUseCase projectAccessUseCase = mock(AuthorizeProjectAccessUseCase.class);
        OpsChatApplicationService chatApplicationService = mock(OpsChatApplicationService.class);
        IncidentQueryApplicationService incidentQueryApplicationService = mock(IncidentQueryApplicationService.class);
        ChangePackageQueryService changePackageService = mock(ChangePackageQueryService.class);
        OpsChangePackagePermissionService changePackagePermissionService = mock(OpsChangePackagePermissionService.class);
        OpsConfigAuditService configAuditService = mock(OpsConfigAuditService.class);
        WorkSessionRunApplicationService workSessionRuns = mock(WorkSessionRunApplicationService.class);
        OpsUserConsoleApplicationService service = new OpsUserConsoleApplicationService(
                projectAccessUseCase,
                chatApplicationService,
                incidentQueryApplicationService,
                changePackageService,
                changePackagePermissionService,
                configAuditService,
                workSessionRuns);
        return new Fixture(service, projectAccessUseCase, chatApplicationService,
                changePackageService, changePackagePermissionService, configAuditService, workSessionRuns);
    }

    private WorkSessionRunSnapshot waitingRun(String runId, String projectId, String query, String hiddenMetadata) {
        Instant now = Instant.parse("2026-08-16T01:00:00Z");
        return new WorkSessionRunSnapshot(
                runId, projectId, "session-1", "20001", "agent-1", 2, "agent-hash",
                "WORKFLOW", WorkSessionRunStatus.WAITING_APPROVAL, "attempt-1", 3, 1,
                "", "", null, false, Map.of(), "manifest-hash",
                Map.of("query", query, "metadata", Map.of("private", hiddenMetadata)),
                Map.of(), "", now.minusSeconds(60), now);
    }

    private Map<String, Object> changePackage() {
        return Map.of(
                "packageId", "cp-1",
                "projectId", "demo-project",
                "objective", "处理 5xx 异常",
                "summary", "需要审批处置方案",
                "status", ChangePackageStatus.REVIEWING.name(),
                "riskLevel", "MEDIUM",
                "version", 1,
                "mcpSteps", List.of(Map.of("operationId", "op-1")));
    }

    private Map<String, Object> auditRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("project_id", "demo-project");
        row.put("module_name", "agent-definition");
        row.put("action_name", "publish");
        row.put("target_id", "demo-ops-agent:1");
        row.put("operator_name", "ops_user");
        row.put("operator_role", "user");
        row.put("client_ip", "127.0.0.1");
        row.put("before_json", "{\"apiKey\":\"secret\"}");
        row.put("after_json", "{\"apiKey\":\"secret\"}");
        row.put("create_time", "2026-06-23 15:40:00");
        return row;
    }

    private record Fixture(OpsUserConsoleApplicationService service,
                           AuthorizeProjectAccessUseCase projectAccessUseCase,
                           OpsChatApplicationService chatApplicationService,
                           ChangePackageQueryService changePackageService,
                           OpsChangePackagePermissionService changePackagePermissionService,
                           OpsConfigAuditService configAuditService,
                           WorkSessionRunApplicationService workSessionRuns) {
    }
}
