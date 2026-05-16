package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.mcp.ManageMcpTemplateUseCase;
import cn.lgs.orbisops.application.mcp.QueryMcpTemplateUseCase;
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

class OpsMcpTemplateAdminControllerTest {

    @Test
    void createTemplateUsesAuthenticatedPrincipal() {
        ManageMcpTemplateUseCase manageTemplates = mock(ManageMcpTemplateUseCase.class);
        QueryMcpTemplateUseCase queryTemplates = mock(QueryMcpTemplateUseCase.class);
        ManageProjectWorkspaceUseCase manageProjects = mock(ManageProjectWorkspaceUseCase.class);
        QueryProjectWorkspaceUseCase queryProjects = mock(QueryProjectWorkspaceUseCase.class);
        OpsMcpTemplateAdminController controller = new OpsMcpTemplateAdminController(
                manageTemplates, queryTemplates, manageProjects, queryProjects);
        when(manageTemplates.create(anyMap(), eq("alice")))
                .thenReturn(Map.of("templateId", "template-1"));
        MockHttpServletRequest request = authenticatedRequest("alice");

        Map<String, Object> result = controller.createTemplate(Map.of(
                "templateId", "template-1",
                "resourceType", "mysql",
                "createBy", "forged-user"), request).getData();

        assertEquals("template-1", result.get("templateId"));
        verify(manageTemplates).create(anyMap(), eq("alice"));
    }

    @Test
    void updateProjectToolUsesAuthenticatedPrincipal() {
        ManageMcpTemplateUseCase manageTemplates = mock(ManageMcpTemplateUseCase.class);
        QueryMcpTemplateUseCase queryTemplates = mock(QueryMcpTemplateUseCase.class);
        ManageProjectWorkspaceUseCase manageProjects = mock(ManageProjectWorkspaceUseCase.class);
        QueryProjectWorkspaceUseCase queryProjects = mock(QueryProjectWorkspaceUseCase.class);
        OpsMcpTemplateAdminController controller = new OpsMcpTemplateAdminController(
                manageTemplates, queryTemplates, manageProjects, queryProjects);
        when(manageProjects.updateProjectTool(eq("project-1"), eq("tool-1"), anyMap(), eq("alice")))
                .thenReturn(Map.of("toolId", "tool-1"));
        MockHttpServletRequest request = authenticatedRequest("alice");

        Map<String, Object> result = controller.updateProjectTool(
                "project-1",
                "tool-1",
                Map.of("actor", "forged-user"),
                request).getData();

        assertEquals("tool-1", result.get("toolId"));
        verify(manageProjects).updateProjectTool(
                eq("project-1"), eq("tool-1"), anyMap(), eq("alice"));
    }

    private MockHttpServletRequest authenticatedRequest(String username) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal(username, "u1", "jwt-1", "admin", false));
        return request;
    }
}
