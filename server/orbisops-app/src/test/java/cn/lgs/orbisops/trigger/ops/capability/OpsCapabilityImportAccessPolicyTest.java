package cn.lgs.orbisops.trigger.ops.capability;

import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.domain.project.model.ProjectAction;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsCapabilityImportAccessPolicyTest {

    @Test
    void platformAdminUsesUnifiedCapabilityActionAndUserIdAsActor() {
        AuthorizeProjectAccessUseCase access = mock(AuthorizeProjectAccessUseCase.class);
        OpsCapabilityImportAccessPolicy policy = new OpsCapabilityImportAccessPolicy(access);
        AdminAuthService.AuthPrincipal principal = new AdminAuthService.AuthPrincipal(
                "admin", "user-1", "jwt-1", AdminAuthService.SCOPE_ADMIN, false);

        policy.assertCanManage("payment", principal);

        assertEquals("user-1", policy.actor(principal));
        verify(access).requireAction(
                "payment", "admin", "user-1", true, ProjectAction.MANAGE_CAPABILITY);
    }

    @Test
    void capabilityManagementDelegatesRoleDecisionToProjectAccessPolicy() {
        AuthorizeProjectAccessUseCase access = mock(AuthorizeProjectAccessUseCase.class);
        OpsCapabilityImportAccessPolicy policy = new OpsCapabilityImportAccessPolicy(access);
        AdminAuthService.AuthPrincipal principal = new AdminAuthService.AuthPrincipal(
                "alice", "user-2", "jwt-2", AdminAuthService.SCOPE_USER, false);
        doNothing()
                .doThrow(new SecurityException(
                        "PROJECT_ACTION_FORBIDDEN：action=MANAGE_CAPABILITY role=VIEWER"))
                .when(access).requireAction(
                        "payment", "alice", "user-2", false, ProjectAction.MANAGE_CAPABILITY);

        policy.assertCanManage("payment", principal);
        SecurityException error = assertThrows(
                SecurityException.class,
                () -> policy.assertCanManage("payment", principal));

        assertEquals(
                "PROJECT_ACTION_FORBIDDEN：action=MANAGE_CAPABILITY role=VIEWER",
                error.getMessage());
        assertEquals("user-2", policy.actor(principal));
        verify(access, org.mockito.Mockito.times(2)).requireAction(
                "payment", "alice", "user-2", false, ProjectAction.MANAGE_CAPABILITY);
    }

    @Test
    void rejectsMissingPrincipalAndProject() {
        OpsCapabilityImportAccessPolicy policy = new OpsCapabilityImportAccessPolicy(
                mock(AuthorizeProjectAccessUseCase.class));

        assertEquals(
                "CAPABILITY_IMPORT_AUTH_REQUIRED",
                assertThrows(SecurityException.class,
                        () -> policy.assertCanManage("payment", null)).getMessage());
        assertEquals(
                "CAPABILITY_IMPORT_PROJECT_REQUIRED",
                assertThrows(IllegalArgumentException.class,
                        () -> policy.requireProjectId(" ")).getMessage());
    }
}
