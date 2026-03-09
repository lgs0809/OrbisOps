package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.application.project.ProjectAccessPort;
import cn.lgs.orbisops.application.project.ProjectMemberApplicationService;
import cn.lgs.orbisops.domain.project.model.ProjectRole;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsChangePackagePermissionServiceTest {

    @Test
    void projectMemberCanPrepareButCannotApproveOrLandByDefault() {
        OpsChangePackagePermissionService service = service(ProjectRole.MEMBER);
        AdminAuthService.AuthPrincipal user = new AdminAuthService.AuthPrincipal("alice", "u1", "jwt-1", "user", false);

        assertDoesNotThrow(() -> service.assertCanPreparePackage("project-1", user));
        assertThrows(SecurityException.class, () -> service.assertCanApprovePackage("project-1", user));
        assertThrows(SecurityException.class, () -> service.assertCanLandPackage("project-1", user));
    }

    @Test
    void approverAndOperatorRolesAreSeparated() {
        OpsChangePackagePermissionService approverService = service(ProjectRole.APPROVER);
        OpsChangePackagePermissionService operatorService = service(ProjectRole.OPERATOR);
        AdminAuthService.AuthPrincipal user = new AdminAuthService.AuthPrincipal("alice", "u1", "jwt-1", "user", false);

        assertDoesNotThrow(() -> approverService.assertCanApprovePackage("project-1", user));
        assertThrows(SecurityException.class, () -> approverService.assertCanLandPackage("project-1", user));

        assertThrows(SecurityException.class, () -> operatorService.assertCanApprovePackage("project-1", user));
        assertDoesNotThrow(() -> operatorService.assertCanLandPackage("project-1", user));
    }

    @Test
    void reviewerCanRejectButCannotApprove() {
        OpsChangePackagePermissionService service = service(ProjectRole.REVIEWER);
        AdminAuthService.AuthPrincipal user = new AdminAuthService.AuthPrincipal("alice", "u1", "jwt-1", "user", false);

        assertDoesNotThrow(() -> service.assertCanRejectPackage("project-1", user));
        assertThrows(SecurityException.class, () -> service.assertCanApprovePackage("project-1", user));
    }

    @Test
    void adminCanApproveAndLandWithoutProjectRole() {
        OpsChangePackagePermissionService service = service(ProjectRole.NONE);
        AdminAuthService.AuthPrincipal admin = new AdminAuthService.AuthPrincipal("admin", "1", "jwt-1", "admin", false);

        assertDoesNotThrow(() -> service.assertCanApprovePackage("project-1", admin));
        assertDoesNotThrow(() -> service.assertCanLandPackage("project-1", admin));
    }

    @Test
    void capabilityProjectionReflectsProjectActionsWithoutThrowing() {
        AdminAuthService.AuthPrincipal user = new AdminAuthService.AuthPrincipal("alice", "u1", "jwt-1", "user", false);

        var approver = service(ProjectRole.APPROVER).capabilities("project-1", user);
        assertTrue(approver.get("canView"));
        assertTrue(approver.get("canRevise"));
        assertTrue(approver.get("canApprove"));
        assertFalse(approver.get("canLand"));

        var operator = service(ProjectRole.OPERATOR).capabilities("project-1", user);
        assertTrue(operator.get("canView"));
        assertFalse(operator.get("canApprove"));
        assertTrue(operator.get("canLand"));
        assertTrue(operator.get("canCleanup"));
    }

    @Test
    void capabilityProjectionDoesNotHideProjectStoreOutageAsPermissionDenial() {
        ProjectAccessPort port = mock(ProjectAccessPort.class);
        ProjectMemberApplicationService members = mock(ProjectMemberApplicationService.class);
        when(port.exists("project-1")).thenThrow(new IllegalStateException("project store unavailable"));
        OpsChangePackagePermissionService service = new OpsChangePackagePermissionService(
                new AuthorizeProjectAccessUseCase(port, members), emptyAuditProvider());
        AdminAuthService.AuthPrincipal user = new AdminAuthService.AuthPrincipal("alice", "u1", "jwt-1", "user", false);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.capabilities("project-1", user));
        assertEquals("project store unavailable", error.getMessage());
    }

    private OpsChangePackagePermissionService service(ProjectRole role) {
        ProjectAccessPort port = mock(ProjectAccessPort.class);
        ProjectMemberApplicationService members = mock(ProjectMemberApplicationService.class);
        when(port.exists("project-1")).thenReturn(true);
        when(port.owner("project-1", "alice", "u1")).thenReturn(false);
        when(members.member("project-1", "alice", "u1")).thenReturn(role != ProjectRole.NONE);
        when(members.role("project-1", "alice", "u1")).thenReturn(role);
        return new OpsChangePackagePermissionService(
                new AuthorizeProjectAccessUseCase(port, members), emptyAuditProvider());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<OpsConfigAuditService> emptyAuditProvider() {
        ObjectProvider<OpsConfigAuditService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
