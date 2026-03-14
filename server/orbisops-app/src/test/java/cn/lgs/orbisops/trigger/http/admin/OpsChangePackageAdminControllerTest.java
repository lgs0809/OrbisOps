package cn.lgs.orbisops.trigger.http.admin;

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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChangePackageAdminControllerTest {

    @Test
    void adminCreateRouteUsesApplicationPreparationUseCase() {
        PrepareChangePackageUseCase preparation = mock(PrepareChangePackageUseCase.class);
        OpsChangePackagePermissionService permissionService = mock(OpsChangePackagePermissionService.class);
        OpsChangePackageAdminController controller = new OpsChangePackageAdminController(
                preparation,
                mock(ValidateChangePackageUseCase.class),
                mock(ReviewChangePackageUseCase.class),
                mock(LandChangePackageUseCase.class),
                mock(ChangePackageQueryService.class),
                permissionService,
                mock(OpsChannelApprovalCardService.class));
        when(preparation.prepare(any())).thenReturn(Map.of("packageId", "cp-1"));
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal("alice", "u1", "jwt-1", "admin", false));

        Response<Map<String, Object>> response = controller.create(Map.of("projectId", "project-1"), servletRequest);

        assertEquals("cp-1", response.getData().get("packageId"));
        verify(permissionService).assertCanPreparePackage(eq("project-1"), any());
        verify(preparation).prepare(any());
    }

    @Test
    void adminListProjectsCapabilityFactsIntoReadModel() {
        ChangePackageQueryService queries = mock(ChangePackageQueryService.class);
        OpsChangePackagePermissionService permissionService = mock(OpsChangePackagePermissionService.class);
        OpsChangePackageAdminController controller = new OpsChangePackageAdminController(
                mock(PrepareChangePackageUseCase.class),
                mock(ValidateChangePackageUseCase.class),
                mock(ReviewChangePackageUseCase.class),
                mock(LandChangePackageUseCase.class),
                queries,
                permissionService,
                mock(OpsChannelApprovalCardService.class));
        when(queries.list(any())).thenReturn(List.of(Map.of(
                "packageId", "cp-1",
                "projectId", "project-1")));
        when(permissionService.capabilities(eq("project-1"), any())).thenReturn(Map.of(
                "canView", true,
                "canApprove", true,
                "canLand", false));
        MockHttpServletRequest servletRequest = authenticatedAdminRequest();

        Response<List<Map<String, Object>>> response = controller.list(
                "project-1", null, null, null, 100, servletRequest);

        Map<String, Object> row = response.getData().get(0);
        assertEquals(true, row.get("canView"));
        assertEquals(true, row.get("canApprove"));
        assertEquals(false, row.get("canLand"));
        assertEquals(Map.of("canView", true, "canApprove", true, "canLand", false), row.get("capabilities"));
        verify(permissionService).capabilities(eq("project-1"), any());
    }

    @Test
    void approvalCardRouteReappliesApprovePermissionBeforeDispatch() {
        ChangePackageQueryService queries = mock(ChangePackageQueryService.class);
        OpsChangePackagePermissionService permissionService = mock(OpsChangePackagePermissionService.class);
        OpsChannelApprovalCardService approvalCards = mock(OpsChannelApprovalCardService.class);
        OpsChangePackageAdminController controller = new OpsChangePackageAdminController(
                mock(PrepareChangePackageUseCase.class),
                mock(ValidateChangePackageUseCase.class),
                mock(ReviewChangePackageUseCase.class),
                mock(LandChangePackageUseCase.class),
                queries,
                permissionService,
                approvalCards);
        when(queries.detail("cp-1")).thenReturn(Map.of("packageId", "cp-1", "projectId", "project-1"));
        doThrow(new SecurityException("denied")).when(permissionService).assertCanApprovePackage(eq("project-1"), any());

        assertThrows(SecurityException.class, () -> controller.sendApprovalCard(
                "cp-1", Map.of("channelId", "channel-1", "target", "room-1"), authenticatedAdminRequest()));

        verify(permissionService).assertCanApprovePackage(eq("project-1"), any());
        verify(approvalCards, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void adminCleanupRequiresLandingCapabilityBeforeCleanup() {
        ChangePackageQueryService queries = mock(ChangePackageQueryService.class);
        LandChangePackageUseCase landing = mock(LandChangePackageUseCase.class);
        OpsChangePackagePermissionService permissionService = mock(OpsChangePackagePermissionService.class);
        OpsChangePackageAdminController controller = new OpsChangePackageAdminController(
                mock(PrepareChangePackageUseCase.class),
                mock(ValidateChangePackageUseCase.class),
                mock(ReviewChangePackageUseCase.class),
                landing,
                queries,
                permissionService,
                mock(OpsChannelApprovalCardService.class));
        when(queries.detail("cp-1")).thenReturn(Map.of("packageId", "cp-1", "projectId", "project-1"));
        when(landing.cleanup(any())).thenReturn(Map.of("status", "CLOSED"));
        MockHttpServletRequest servletRequest = authenticatedAdminRequest();

        Response<Map<String, Object>> response = controller.cleanup("cp-1", Map.of("reason", "done"), servletRequest);

        assertEquals("CLOSED", response.getData().get("status"));
        verify(permissionService).assertCanLandPackage(eq("project-1"), any());
        verify(landing).cleanup(any());
    }

    private MockHttpServletRequest authenticatedAdminRequest() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal("alice", "u1", "jwt-1", "admin", false));
        return servletRequest;
    }
}
