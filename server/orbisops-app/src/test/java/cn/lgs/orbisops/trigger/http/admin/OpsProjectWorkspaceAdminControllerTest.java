package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.project.ManageProjectWorkspaceUseCase;
import cn.lgs.orbisops.application.project.QueryProjectWorkspaceUseCase;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsProjectWorkspaceAdminControllerTest {

    @Test
    void createProjectUsesAuthenticatedPrincipal() {
        ManageProjectWorkspaceUseCase manageProjects = mock(ManageProjectWorkspaceUseCase.class);
        QueryProjectWorkspaceUseCase queryProjects = mock(QueryProjectWorkspaceUseCase.class);
        OpsProjectWorkspaceAdminController controller =
                new OpsProjectWorkspaceAdminController(manageProjects, queryProjects);
        when(manageProjects.createProject(anyMap(), eq("alice")))
                .thenReturn(Map.of("projectId", "project-1"));
        MockHttpServletRequest request = authenticatedRequest("alice");

        Map<String, Object> result = controller.createProject(Map.of(
                "projectId", "project-1",
                "actor", "forged-user"), request).getData();

        assertEquals("project-1", result.get("projectId"));
        verify(manageProjects).createProject(anyMap(), eq("alice"));
    }

    @Test
    void addResourceUsesAuthenticatedPrincipal() {
        ManageProjectWorkspaceUseCase manageProjects = mock(ManageProjectWorkspaceUseCase.class);
        QueryProjectWorkspaceUseCase queryProjects = mock(QueryProjectWorkspaceUseCase.class);
        OpsProjectWorkspaceAdminController controller =
                new OpsProjectWorkspaceAdminController(manageProjects, queryProjects);
        when(manageProjects.addResource(anyMap(), eq("alice")))
                .thenReturn(Map.of("resourceId", "resource-1"));
        MockHttpServletRequest request = authenticatedRequest("alice");

        Map<String, Object> result = controller.addResource(Map.of(
                "projectId", "project-1",
                "resourceId", "resource-1",
                "actor", "forged-user"), request).getData();

        assertEquals("resource-1", result.get("resourceId"));
        verify(manageProjects).addResource(anyMap(), eq("alice"));
    }

    private MockHttpServletRequest authenticatedRequest(String username) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal(username, "u1", "jwt-1", "admin", false));
        return request;
    }
}
