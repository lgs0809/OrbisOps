package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackagePermissionService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChangePackageControlToolProviderTest {

    @Test
    void startLandingOnlyIssuesGovernedWorkflowCommandWithExactVersionAndHash() {
        OpsToolExecutionService execution = mock(OpsToolExecutionService.class);
        OpsChangePackagePermissionService permissions = mock(OpsChangePackagePermissionService.class);
        AdminAuthService.AuthPrincipal principal = principal();
        when(execution.execute(any(), eq("alice"))).thenReturn(Map.of("status", "LANDING_RUNNING"));
        OpsChangePackageControlToolProvider provider = new OpsChangePackageControlToolProvider(execution, permissions);
        ToolCallback start = tool(provider.build("project-1", "alice", "run-1", principal),
                OpsChangePackageControlToolProvider.START_LANDING_TOOL);

        start.call("{\"packageId\":\"cp-1\",\"version\":3,\"packageHash\":\"hash-3\",\"idempotencyKey\":\"idem-1\"}");

        verify(permissions).assertCanLandPackage("project-1", principal);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> request = ArgumentCaptor.forClass(Map.class);
        verify(execution).execute(request.capture(), eq("alice"));
        assertEquals("change_package", request.getValue().get("toolsetId"));
        assertEquals("change_package_start_landing", request.getValue().get("toolName"));
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) request.getValue().get("arguments");
        assertEquals("cp-1", arguments.get("packageId"));
        assertEquals(3, arguments.get("version"));
        assertEquals("hash-3", arguments.get("packageHash"));
        assertEquals("idem-1", arguments.get("idempotencyKey"));
    }

    @Test
    void approveCarriesExactFrozenPointerAndDoesNotLand() {
        OpsToolExecutionService execution = mock(OpsToolExecutionService.class);
        OpsChangePackagePermissionService permissions = mock(OpsChangePackagePermissionService.class);
        AdminAuthService.AuthPrincipal principal = principal();
        when(execution.execute(any(), eq("alice"))).thenReturn(Map.of("status", "APPROVED"));
        OpsChangePackageControlToolProvider provider = new OpsChangePackageControlToolProvider(execution, permissions);
        ToolCallback approve = tool(provider.build("project-1", "alice", "run-1", principal),
                OpsChangePackageControlToolProvider.APPROVE_TOOL);

        approve.call("{\"packageId\":\"cp-2\",\"version\":7,\"packageHash\":\"hash-7\",\"comment\":\"ok\"}");

        verify(permissions).assertCanApprovePackage("project-1", principal);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> request = ArgumentCaptor.forClass(Map.class);
        verify(execution).execute(request.capture(), eq("alice"));
        assertEquals("change_package_approve", request.getValue().get("toolName"));
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) request.getValue().get("arguments");
        assertEquals(7, arguments.get("version"));
        assertEquals("hash-7", arguments.get("packageHash"));
        assertEquals(AdminAuthService.SCOPE_USER, arguments.get("actorScope"));
    }

    @Test
    void permissionDenialStopsBeforeUnifiedToolExecution() {
        OpsToolExecutionService execution = mock(OpsToolExecutionService.class);
        OpsChangePackagePermissionService permissions = mock(OpsChangePackagePermissionService.class);
        AdminAuthService.AuthPrincipal principal = principal();
        doThrow(new SecurityException("denied"))
                .when(permissions).assertCanLandPackage("project-1", principal);
        OpsChangePackageControlToolProvider provider = new OpsChangePackageControlToolProvider(execution, permissions);
        ToolCallback start = tool(provider.build("project-1", "alice", "run-1", principal),
                OpsChangePackageControlToolProvider.START_LANDING_TOOL);

        ToolExecutionException error = assertThrows(ToolExecutionException.class, () -> start.call(
                "{\"packageId\":\"cp-1\",\"version\":3,\"packageHash\":\"hash-3\"}"));

        assertEquals(SecurityException.class, error.getCause().getClass());
        verify(execution, never()).execute(any(), any());
    }

    private AdminAuthService.AuthPrincipal principal() {
        return new AdminAuthService.AuthPrincipal(
                "alice-name", "alice", "jwt-1", AdminAuthService.SCOPE_USER, false);
    }

    private ToolCallback tool(List<ToolCallback> tools, String name) {
        return tools.stream()
                .filter(tool -> name.equals(tool.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();
    }
}
