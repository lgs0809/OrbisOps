package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.application.changepackage.ChangePackageListQuery;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.application.project.ProjectCatalogEntry;
import cn.lgs.orbisops.domain.project.model.ProjectRole;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackagePermissionService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsUserConsoleApplicationServiceTest {

    @Test
    void shouldReturnUserDashboardFromRealServices() {
        Fixture fixture = fixture();
        when(fixture.projectAccessUseCase.catalog("ops_user", "20001", false)).thenReturn(List.of(
                project("demo-project", "示例系统", "demo-ops-agent")));
        when(fixture.projectAccessUseCase.role("demo-project", "ops_user", "20001", false))
                .thenReturn(ProjectRole.APPROVER);
        when(fixture.agentDefinitionApplicationService.listAgents("demo-project")).thenReturn(List.of(Map.of(
                "agentId", "demo-ops-agent",
                "name", "示例运维 Agent")));
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

        Map<String, Object> overview = fixture.service.dashboardOverview("ops_user", "20001");

        assertEquals(1, ((List<?>) overview.get("availableProjects")).size());
        @SuppressWarnings("unchecked")
        Map<String, Object> project = (Map<String, Object>) ((List<?>) overview.get("availableProjects")).get(0);
        assertEquals("APPROVER", project.get("projectRole"));
        assertEquals(1, ((List<?>) overview.get("availableAgents")).size());
        assertEquals(1, ((List<?>) overview.get("recentSessions")).size());
        assertEquals(1, ((List<?>) overview.get("pendingExecutions")).size());
        assertEquals(1, ((List<?>) overview.get("auditReminders")).size());
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
        OpsAgentDefinitionApplicationService agentDefinitionApplicationService = mock(OpsAgentDefinitionApplicationService.class);
        OpsChatApplicationService chatApplicationService = mock(OpsChatApplicationService.class);
        IncidentQueryApplicationService incidentQueryApplicationService = mock(IncidentQueryApplicationService.class);
        ChangePackageQueryService changePackageService = mock(ChangePackageQueryService.class);
        OpsChangePackagePermissionService changePackagePermissionService = mock(OpsChangePackagePermissionService.class);
        OpsConfigAuditService configAuditService = mock(OpsConfigAuditService.class);
        OpsUserConsoleApplicationService service = new OpsUserConsoleApplicationService(
                projectAccessUseCase,
                agentDefinitionApplicationService,
                chatApplicationService,
                incidentQueryApplicationService,
                changePackageService,
                changePackagePermissionService,
                configAuditService);
        return new Fixture(service, projectAccessUseCase, agentDefinitionApplicationService,
                chatApplicationService, changePackageService, changePackagePermissionService, configAuditService);
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
                           OpsAgentDefinitionApplicationService agentDefinitionApplicationService,
                           OpsChatApplicationService chatApplicationService,
                           ChangePackageQueryService changePackageService,
                           OpsChangePackagePermissionService changePackagePermissionService,
                           OpsConfigAuditService configAuditService) {
    }
}
