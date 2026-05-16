package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.OpsExecutionResourceDTO;
import cn.lgs.orbisops.application.execution.ExecutionAdapterTemplateApplicationService;
import cn.lgs.orbisops.application.execution.ExecutionAdapterTemplateCommands;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.trigger.application.execution.OpsExecutionAdapterTemplateCommandMapper;
import cn.lgs.orbisops.trigger.application.execution.OpsExecutionResourceAdapter;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsExecutionAdapterTemplateAdminControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void createTemplateUsesAuthenticatedPrincipalAndTypedMapper() {
        ExecutionAdapterTemplateApplicationService<OpsExecutionResourceDTO> templates =
                mock(ExecutionAdapterTemplateApplicationService.class);
        OpsExecutionResourceAdapter resources = mock(OpsExecutionResourceAdapter.class);
        OpsExecutionAdapterTemplateAdminController controller =
                new OpsExecutionAdapterTemplateAdminController(
                        templates, resources, new OpsExecutionAdapterTemplateCommandMapper());
        when(templates.create(any(ExecutionAdapterTemplateCommands.Mutation.class)))
                .thenReturn(Map.of("adapterTemplateId", "template-1"));
        MockHttpServletRequest request = authenticatedRequest("alice");

        Map<String, Object> result = controller.createTemplate(Map.of(
                "adapterTemplateId", "template-1",
                "adapterType", "local-java-service",
                "createBy", "forged-user"), request).getData();

        assertEquals("template-1", result.get("adapterTemplateId"));
        ArgumentCaptor<ExecutionAdapterTemplateCommands.Mutation> command =
                ArgumentCaptor.forClass(ExecutionAdapterTemplateCommands.Mutation.class);
        verify(templates).create(command.capture());
        assertEquals("alice", command.getValue().actor());
        assertEquals("template-1", command.getValue().templateId().value());
        assertEquals(ExecutionAdapterType.LOCAL_JAVA_SERVICE, command.getValue().adapterType().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateTargetUsesAuthenticatedPrincipalAndTypedCommand() {
        ExecutionAdapterTemplateApplicationService<OpsExecutionResourceDTO> templates =
                mock(ExecutionAdapterTemplateApplicationService.class);
        OpsExecutionResourceAdapter resources = mock(OpsExecutionResourceAdapter.class);
        OpsExecutionAdapterTemplateAdminController controller =
                new OpsExecutionAdapterTemplateAdminController(
                        templates, resources, new OpsExecutionAdapterTemplateCommandMapper());
        OpsExecutionResourceDTO target = mock(OpsExecutionResourceDTO.class);
        when(templates.generateTarget(any(ExecutionAdapterTemplateCommands.TargetGeneration.class)))
                .thenReturn(target);
        MockHttpServletRequest request = authenticatedRequest("alice");

        OpsExecutionResourceDTO result = controller.generateProjectExecutionTarget(
                "project-1", Map.of("adapterTemplateId", "template-1"), request).getData();

        assertEquals(target, result);
        ArgumentCaptor<ExecutionAdapterTemplateCommands.TargetGeneration> command =
                ArgumentCaptor.forClass(ExecutionAdapterTemplateCommands.TargetGeneration.class);
        verify(templates).generateTarget(command.capture());
        assertEquals("project-1", command.getValue().projectId());
        assertEquals("template-1", command.getValue().templateId());
        assertEquals("alice", command.getValue().actor());
    }

    private MockHttpServletRequest authenticatedRequest(String username) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal(username, "u1", "jwt-1", "admin", false));
        return request;
    }
}
