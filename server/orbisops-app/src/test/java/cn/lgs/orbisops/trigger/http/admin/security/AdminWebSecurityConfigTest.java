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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "POST,/api/v1/admin/admin-user/logout,true",
        "POST,/api/v1/user/account/password,true",
        "GET,/api/v1/user/account,true",
        "GET,/api/v1/admin/admin-user/logout,false",
        "POST,/api/v1/admin/admin-user/logout/extra,false",
        "POST,/api/v1/admin/admin-user/create,false",
        "GET,/api/v1/admin/admin-user/query-all,false"
    })
    void selfServiceDoesNotExpandAdministratorNamespace(String method, String path, boolean expected) throws Exception {
        var authentication = mock(AdminAuthService.class);
        var rateLimit = mock(OpsRateLimitService.class);
        when(authentication.verifyAuthorization(anyString(), any(String[].class))).thenReturn(Optional.of(
                new AdminAuthService.AuthPrincipal("member", "u-1", "j-1", "user", false)));
        when(rateLimit.tryAcquire(anyString(), anyString())).thenReturn(true);
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path); request.addHeader("Authorization", "Bearer existing-token");
        var response = new MockHttpServletResponse();
        assertEquals(expected, new AdminWebSecurityConfig.AdminAuthInterceptor(authentication, rateLimit).preHandle(request, response, new Object()));
        assertEquals(expected ? 200 : 403, response.getStatus());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "user,POST,/api/v1/user/account/password,true",
        "admin,POST,/api/v1/admin/admin-user/logout,true",
        "user,GET,/api/v1/user/account,true",
        "admin,POST,/api/v1/admin/admin-user/create,false",
        "user,POST,/api/v1/user/chat,false",
        "admin,GET,/api/v1/agent/trace,false"
    })
    void initialCredentialCannotAccessBusinessOrAdminBeforeChange(String scope, String method, String path, boolean expected) throws Exception {
        var authentication = mock(AdminAuthService.class);
        var rateLimit = mock(OpsRateLimitService.class);
        when(authentication.verifyAuthorization(anyString(), any(String[].class))).thenReturn(Optional.of(
                new AdminAuthService.AuthPrincipal("member", "u-1", "j-1", scope, false, true)));
        when(rateLimit.tryAcquire(anyString(), anyString())).thenReturn(true);
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path); request.addHeader("Authorization", "Bearer initial-session");
        var response = new MockHttpServletResponse();
        assertEquals(expected, new AdminWebSecurityConfig.AdminAuthInterceptor(authentication, rateLimit).preHandle(request, response, new Object()));
        if (!expected) { assertEquals(403,response.getStatus()); assertTrue(response.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED")); }
    }

    @Test
    void anonymousLogoutCannotBypassAuthentication() throws Exception {
        var request = new MockHttpServletRequest("POST","/api/v1/admin/admin-user/logout");
        request.setServletPath("/api/v1/admin/admin-user/logout");
        var response = new MockHttpServletResponse();
        assertFalse(new AdminWebSecurityConfig.AdminAuthInterceptor(mock(AdminAuthService.class),mock(OpsRateLimitService.class)).preHandle(request,response,new Object()));
        assertEquals(401,response.getStatus());
    }

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
