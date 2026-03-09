package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionApplicationService;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackagePermissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsToolExecutionReconciliationAdminControllerTest {

    @Test
    void resolutionRequiresLandPermissionAndUsesAuthenticatedActor() {
        ToolExecutionApplicationService tools = mock(ToolExecutionApplicationService.class);
        OpsChangePackagePermissionService permissions = mock(OpsChangePackagePermissionService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        AdminAuthService.AuthPrincipal principal = new AdminAuthService.AuthPrincipal(
                "admin-name", "admin-id", "jwt-1", AdminAuthService.SCOPE_ADMIN, false);
        when(request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE)).thenReturn(principal);
        when(tools.unresolvedSideEffects("project-1", "landing-run-1")).thenReturn(List.of());
        OpsToolExecutionReconciliationAdminController controller =
                new OpsToolExecutionReconciliationAdminController(tools, permissions);

        controller.resolve(Map.of(
                "projectId", "project-1",
                "runId", "landing-run-1",
                "idempotencyKey", "mcp:idem-1",
                "resolution", "CONFIRMED_NOT_EXECUTED",
                "evidenceId", "evidence-1",
                "evidenceHash", "a".repeat(64),
                "note", "authoritative read confirms no change"), request);

        verify(permissions).assertCanLandPackage("project-1", principal);
        ArgumentCaptor<ToolExecutionIdempotencyPort.ResolveSideEffectCommand> captor =
                ArgumentCaptor.forClass(ToolExecutionIdempotencyPort.ResolveSideEffectCommand.class);
        verify(tools).resolveUncertainSideEffect(captor.capture());
        assertEquals("admin-id", captor.getValue().actor());
        assertEquals(ToolExecutionIdempotencyPort.SideEffectResolution.CONFIRMED_NOT_EXECUTED,
                captor.getValue().resolution());
    }

    @Test
    void permissionDenialCannotMutateReconciliationLedger() {
        ToolExecutionApplicationService tools = mock(ToolExecutionApplicationService.class);
        OpsChangePackagePermissionService permissions = mock(OpsChangePackagePermissionService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        AdminAuthService.AuthPrincipal principal = new AdminAuthService.AuthPrincipal(
                "user", "user-1", "jwt-1", AdminAuthService.SCOPE_USER, false);
        when(request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE)).thenReturn(principal);
        doThrow(new SecurityException("denied"))
                .when(permissions).assertCanLandPackage("project-1", principal);
        OpsToolExecutionReconciliationAdminController controller =
                new OpsToolExecutionReconciliationAdminController(tools, permissions);

        assertThrows(SecurityException.class, () -> controller.resolve(Map.of(
                "projectId", "project-1",
                "runId", "landing-run-1",
                "idempotencyKey", "mcp:idem-1",
                "resolution", "CONFIRMED_SUCCEEDED",
                "evidenceId", "evidence-1",
                "evidenceHash", "a".repeat(64),
                "note", "verified"), request));

        verify(tools, never()).resolveUncertainSideEffect(any());
    }
}
