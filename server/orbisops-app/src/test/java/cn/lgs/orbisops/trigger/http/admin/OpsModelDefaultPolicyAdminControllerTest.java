package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicyApplicationService;
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

class OpsModelDefaultPolicyAdminControllerTest {

    @Test
    void updateUsesAuthenticatedPrincipal() {
        ModelDefaultPolicyApplicationService policies = mock(ModelDefaultPolicyApplicationService.class);
        OpsModelDefaultPolicyAdminController controller =
                new OpsModelDefaultPolicyAdminController(policies);
        when(policies.update(eq("project-1"), anyMap(), eq("alice")))
                .thenReturn(Map.of("projectId", "project-1", "status", "ENABLED"));
        MockHttpServletRequest request = authenticatedRequest("alice");

        Map<String, Object> result = controller.update(
                "project-1",
                Map.of("status", "ENABLED", "actor", "forged-user"),
                request).getData();

        assertEquals("project-1", result.get("projectId"));
        verify(policies).update(eq("project-1"), anyMap(), eq("alice"));
    }

    private MockHttpServletRequest authenticatedRequest(String username) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE,
                new AdminAuthService.AuthPrincipal(username, "u1", "jwt-1", "admin", false));
        return request;
    }
}
