package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.OpsCodeDeliveryRequestDTO;
import cn.lgs.orbisops.api.dto.OpsRepairWorkspaceRequestDTO;
import cn.lgs.orbisops.application.repair.CodeDeliveryApplicationService;
import cn.lgs.orbisops.application.repair.RepairWorkspaceApplicationService;
import cn.lgs.orbisops.domain.repair.model.CodeDelivery;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryMode;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.trigger.application.repair.OpsCodeDeliveryMapper;
import cn.lgs.orbisops.trigger.application.repair.OpsRepairWorkspaceMapper;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRepairWorkspaceAdminControllerTest {

    private static final String BASE = "0123456789abcdef0123456789abcdef01234567";
    private static final String REPAIR = "89abcdef0123456789abcdef0123456789abcdef";

    @Test
    void createPublishAndRefreshUseAuthenticatedPrincipal() {
        RepairWorkspaceApplicationService workspaces = mock(RepairWorkspaceApplicationService.class);
        CodeDeliveryApplicationService deliveries = mock(CodeDeliveryApplicationService.class);
        OpsRepairWorkspaceAdminController controller = controller(workspaces, deliveries);
        when(workspaces.createAndVerify(any(), eq("alice"))).thenReturn(workspace());
        when(deliveries.publish(eq("repair-1"), any(), eq("alice"))).thenReturn(delivery("QUEUED"));
        when(deliveries.refreshCi("delivery-1", "alice")).thenReturn(delivery("SUCCESS"));
        MockHttpServletRequest request = authenticatedRequest("alice");
        OpsRepairWorkspaceRequestDTO create = OpsRepairWorkspaceRequestDTO.builder()
                .projectId("project-1").serviceId("service-1").environment("prod")
                .summary("fix").unifiedDiff("patch").baseCommit(BASE).build();
        OpsCodeDeliveryRequestDTO publish = OpsCodeDeliveryRequestDTO.builder()
                .mode("GITHUB_PR").title("review fix").baseBranch("main").build();

        assertEquals("repair-1", controller.create(create, request).getData().getWorkspaceId());
        assertEquals("QUEUED", controller.publish("repair-1", publish, request).getData().getCiStatus());
        assertEquals("SUCCESS", controller.refreshCi("delivery-1", request).getData().getCiStatus());

        verify(workspaces).createAndVerify(any(), eq("alice"));
        verify(deliveries).publish(eq("repair-1"), any(), eq("alice"));
        verify(deliveries).refreshCi("delivery-1", "alice");
    }

    @Test
    void listAndDetailRemainDirectReadResponses() {
        RepairWorkspaceApplicationService workspaces = mock(RepairWorkspaceApplicationService.class);
        CodeDeliveryApplicationService deliveries = mock(CodeDeliveryApplicationService.class);
        OpsRepairWorkspaceAdminController controller = controller(workspaces, deliveries);
        when(workspaces.list("project-1")).thenReturn(List.of(workspace()));
        when(workspaces.get("repair-1")).thenReturn(workspace());
        when(deliveries.list("repair-1")).thenReturn(List.of(delivery("LOCAL_VERIFIED")));

        assertEquals(1, controller.list("project-1").getData().size());
        assertEquals("VERIFIED", controller.detail("repair-1").getData().getStatus());
        assertEquals(1, controller.listDeliveries("repair-1").getData().size());
    }

    private OpsRepairWorkspaceAdminController controller(
            RepairWorkspaceApplicationService workspaces,
            CodeDeliveryApplicationService deliveries) {
        return new OpsRepairWorkspaceAdminController(
                workspaces, deliveries, new OpsRepairWorkspaceMapper(), new OpsCodeDeliveryMapper());
    }

    private RepairWorkspace workspace() {
        return new RepairWorkspace(
                "repair-1", "project-1", "service-1", "repo-1", "prod", BASE, REPAIR,
                RepairWorkspaceStatus.VERIFIED, "fix", "patch", List.of("module/src/App.java"),
                "MAVEN_VERIFY", "mvn test", 0, "ok", "", "", 0L,
                "alice", "now", "now");
    }

    private CodeDelivery delivery(String status) {
        return new CodeDelivery(
                "delivery-1", "repair-1", "project-1", "service-1", CodeDeliveryMode.GITHUB_PR,
                "ops-repair/service-1/repair-1", REPAIR, "https://github.test/pr/1",
                status, "https://github.test/run/1", "alice", "now", "now");
    }

    private MockHttpServletRequest authenticatedRequest(String username) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal(username, "u1", "jwt-1", "admin", false));
        return request;
    }
}
