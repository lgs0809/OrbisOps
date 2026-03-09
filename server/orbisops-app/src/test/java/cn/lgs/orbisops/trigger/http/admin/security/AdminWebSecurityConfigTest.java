package cn.lgs.orbisops.trigger.http.admin.security;

import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminWebSecurityConfigTest {

    @Test
    void shouldRejectAuthenticatedUserFromAdminApiWithForbidden() throws Exception {
        AdminAuthService authService = mock(AdminAuthService.class);
        OpsRateLimitService rateLimitService = mock(OpsRateLimitService.class);
        when(authService.verifyServiceToken(any())).thenReturn(false);
        when(authService.verifyAuthorization(
                anyString(),
                any(String[].class)))
                .thenReturn(Optional.of(new AdminAuthService.AuthPrincipal(
                        "ops_user",
                        "20001",
                        "jwt-1",
                        AdminAuthService.SCOPE_USER,
                        false)));

        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/admin/ops/config-audits");
        request.setServletPath("/api/v1/admin/ops/config-audits");
        request.addHeader("Authorization", "Bearer user-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = new AdminWebSecurityConfig.AdminAuthInterceptor(authService, rateLimitService)
                .preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(403, response.getStatus());
        verify(authService).verifyAuthorization(
                "Bearer user-token",
                AdminAuthService.SCOPE_ADMIN,
                AdminAuthService.SCOPE_USER);
    }

    @Test
    void shouldAllowAuthenticatedUserForUserApi() throws Exception {
        AdminAuthService authService = mock(AdminAuthService.class);
        OpsRateLimitService rateLimitService = mock(OpsRateLimitService.class);
        when(authService.verifyServiceToken(any())).thenReturn(false);
        when(authService.verifyAuthorization(
                anyString(),
                any(String[].class)))
                .thenReturn(Optional.of(new AdminAuthService.AuthPrincipal(
                        "ops_user",
                        "20001",
                        "jwt-1",
                        AdminAuthService.SCOPE_USER,
                        false)));
        when(rateLimitService.tryAcquire("ops_user", "/api/v1/user/my-audits")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/user/my-audits");
        request.setServletPath("/api/v1/user/my-audits");
        request.addHeader("Authorization", "Bearer user-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = new AdminWebSecurityConfig.AdminAuthInterceptor(authService, rateLimitService)
                .preHandle(request, response, new Object());

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
        verify(authService).verifyAuthorization(
                "Bearer user-token",
                AdminAuthService.SCOPE_ADMIN,
                AdminAuthService.SCOPE_USER);
    }
}
