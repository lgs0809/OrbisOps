package cn.lgs.orbisops.trigger.http.admin.security;

import cn.lgs.orbisops.api.dto.AdminUserRequestDTO;
import cn.lgs.orbisops.api.dto.AdminUserResponseDTO;
import cn.lgs.orbisops.application.security.JwtRevocationPort;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthSettings;
import cn.lgs.orbisops.trigger.application.security.AdminUserApplicationService;
import cn.lgs.orbisops.trigger.http.admin.AdminUserAdminController;
import com.auth0.jwt.JWT;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPhoneHttpBoundaryTest {
    private static final String USERS = "/api/v1/admin/admin-user";

    @Test
    void anonymousCannotRegisterThroughAdminCreationEndpoint() throws Exception {
        var service = mock(AdminUserApplicationService.class);
        var auth = authService();
        mvc(service, auth).perform(post(USERS + "/create").contentType(MediaType.APPLICATION_JSON)
                .content(createRequest())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void normalUserCannotEscalateBySubmittingAdminRole() throws Exception {
        var service = mock(AdminUserApplicationService.class);
        var auth = authService();
        String token = auth.issueToken(account(AdminUserRole.USER));
        mvc(service, auth).perform(post(USERS + "/create").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(createRequest()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void existingAdminCanCreatePhoneUserAndIdentityClaimsDoNotBecomeThePhone() throws Exception {
        var service = mock(AdminUserApplicationService.class);
        when(service.create(any())).thenReturn(true);
        var auth = authService();
        String token = auth.issueToken(account(AdminUserRole.ADMIN));
        mvc(service, auth).perform(post(USERS + "/create").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(createRequest())).andExpect(status().isOk());
        var captured = org.mockito.ArgumentCaptor.forClass(AdminUserRequestDTO.class);
        verify(service).create(captured.capture());
        assertEquals("13800138000", captured.getValue().getPhone());
        assertEquals("user-stable", JWT.decode(token).getClaim("userId").asString());
        assertEquals("original-name", JWT.decode(token).getSubject());
        assertNull(JWT.decode(token).getClaim("phone").asString());
        assertFalse(captured.getValue().toString().contains("StrongPass1"));
        assertFalse(captured.getValue().toString().contains("13800138000"));
    }

    @Test
    void accountCatalogResponseContainsPhoneButNoPasswordOrCredential() throws Exception {
        var service = mock(AdminUserApplicationService.class);
        when(service.queryById(1L)).thenReturn(AdminUserResponseDTO.builder().id(1L)
                .userId("user-stable").username("original-name").phone("13800138000")
                .userRole("user").status(1).build());
        var auth = authService();
        String content = mvc(service, auth).perform(get(USERS + "/query-by-id/1")
                .header("Authorization", "Bearer " + auth.issueToken(account(AdminUserRole.ADMIN))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(content).get("data");
        assertEquals("13800138000", json.get("phone").asText());
        assertEquals("user-stable", json.get("userId").asText());
        assertFalse(json.has("password"));
        assertFalse(json.has("credential"));
        assertFalse(json.has("passwordHash"));
    }

    private MockMvc mvc(AdminUserApplicationService service, AdminAuthService auth) {
        var limiter = mock(OpsRateLimitService.class);
        when(limiter.tryAcquire(anyString(), anyString())).thenReturn(true);
        return MockMvcBuilders.standaloneSetup(new AdminUserAdminController(service))
                .addInterceptors(new AdminWebSecurityConfig.AdminAuthInterceptor(auth, limiter)).build();
    }

    private AdminAuthService authService() {
        return new AdminAuthService(mock(JwtRevocationPort.class), new AdminAuthSettings(
                "synthetic-test-signing-material-for-phone-http", 1L, "", true));
    }

    private AdminUserAccount account(AdminUserRole role) {
        return new AdminUserAccount(1L, "user-stable", "original-name", "encoded", role,
                1, null, null, "13900139000");
    }

    private String createRequest() {
        return "{\"username\":\"值班同事\",\"phone\":\"13800138000\",\"password\":\"StrongPass1\","
                + "\"userRole\":\"admin\",\"status\":1}";
    }
}
