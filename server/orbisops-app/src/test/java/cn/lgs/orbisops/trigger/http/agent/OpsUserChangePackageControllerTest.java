package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.changepackage.LandChangePackageUseCase;
import cn.lgs.orbisops.application.changepackage.PrepareChangePackageUseCase;
import cn.lgs.orbisops.application.changepackage.ReviewChangePackageUseCase;
import cn.lgs.orbisops.application.changepackage.ValidateChangePackageUseCase;
import cn.lgs.orbisops.trigger.application.channel.OpsChannelApprovalCardService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackagePermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsUserChangePackageControllerTest {

    @Test
    void listRequiresProjectBoundaryBeforeQueryingPackages() {
        ChangePackageQueryService queries = mock(ChangePackageQueryService.class);
        OpsChangePackagePermissionService permissionService = mock(OpsChangePackagePermissionService.class);
        OpsUserChangePackageController controller = controller(queries, permissionService);

        assertThrows(IllegalArgumentException.class,
                () -> controller.list(null, null, null, 100, authenticatedUserRequest()));
    }

    @Test
    void listChecksProjectAccessAndProjectsCapabilityFacts() {
        ChangePackageQueryService queries = mock(ChangePackageQueryService.class);
        OpsChangePackagePermissionService permissionService = mock(OpsChangePackagePermissionService.class);
        OpsUserChangePackageController controller = controller(queries, permissionService);
        when(queries.list(any())).thenReturn(List.of(
                Map.of("packageId", "cp-1", "projectId", "project-1"),
                Map.of("packageId", "cp-cross-project", "projectId", "project-2")));
        when(permissionService.capabilities(eq("project-1"), any())).thenReturn(Map.of(
                "canView", true,
                "canSubmitReview", true,
                "canApprove", false,
                "canLand", false));
        MockHttpServletRequest servletRequest = authenticatedUserRequest();

        Response<List<Map<String, Object>>> response = controller.list(
                "project-1", "session-1", "REVIEWING", 50, servletRequest);

        assertEquals(1, response.getData().size());
        Map<String, Object> row = response.getData().get(0);
        assertEquals(true, row.get("canView"));
        assertEquals(true, row.get("canSubmitReview"));
        assertEquals(false, row.get("canApprove"));
        assertEquals(false, row.get("canLand"));
        assertEquals(Map.of(
                "canView", true,
                "canSubmitReview", true,
                "canApprove", false,
                "canLand", false), row.get("capabilities"));
        verify(permissionService).assertCanViewPackage(eq("project-1"), any());
        verify(permissionService).capabilities(eq("project-1"), any());
    }

    @Test
    void verificationRequiresProjectViewAndLandingPermissionBeforeInvocation() {
        var queries = mock(ChangePackageQueryService.class);
        var permissions = mock(OpsChangePackagePermissionService.class);
        var landing = mock(LandChangePackageUseCase.class);
        var controller = new OpsUserChangePackageController(mock(PrepareChangePackageUseCase.class),
                mock(ValidateChangePackageUseCase.class), mock(ReviewChangePackageUseCase.class), landing,
                queries, permissions, mock(OpsChannelApprovalCardService.class));
        when(queries.detail("cp-1")).thenReturn(Map.of("projectId", "project-1"));
        org.mockito.Mockito.doThrow(new SecurityException("denied"))
                .when(permissions).assertCanLandPackage(eq("project-1"), any());
        assertThrows(SecurityException.class, () -> controller.verifyLanding("cp-1", authenticatedUserRequest()));
        org.mockito.Mockito.verifyNoInteractions(landing);
        verify(permissions).assertCanViewPackage(eq("project-1"), any());
    }

    private OpsUserChangePackageController controller(ChangePackageQueryService queries,
                                                       OpsChangePackagePermissionService permissionService) {
        return new OpsUserChangePackageController(
                mock(PrepareChangePackageUseCase.class),
                mock(ValidateChangePackageUseCase.class),
                mock(ReviewChangePackageUseCase.class),
                mock(LandChangePackageUseCase.class),
                queries,
                permissionService,
                mock(OpsChannelApprovalCardService.class));
    }

    private MockHttpServletRequest authenticatedUserRequest() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal("alice", "u1", "jwt-1", "user", false));
        return servletRequest;
    }
}
