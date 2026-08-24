package cn.lgs.orbisops.trigger.http.admin.security;

import cn.lgs.orbisops.application.security.AdminUserCatalogPort;
import cn.lgs.orbisops.application.security.AdminUserAuthenticationException;
import cn.lgs.orbisops.application.security.JwtRevocationPort;
import cn.lgs.orbisops.application.security.JwtRevocationUnavailableException;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.infrastructure.adapter.repository.JwtRevocationRepository;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthSettings;
import cn.lgs.orbisops.trigger.application.security.AdminUserApplicationService;
import cn.lgs.orbisops.trigger.http.admin.AdminUserAdminController;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JwtRevocationFailureBoundaryTest {
    private static final String PRIVATE_ERROR = "private-driver-marker phone=13800138000 password=StrongPass1";

    @Test
    void enabledStoreWithMissingTemplateCannotPretendRevocationWasDisabled() {
        var store = store(new DefaultListableBeanFactory().getBeanProvider(JdbcTemplate.class), true);
        assertTrue(store.available());
        var read = assertThrows(JwtRevocationUnavailableException.class, () -> store.isRevoked("test-jti"));
        var write = assertThrows(JwtRevocationUnavailableException.class,
                () -> store.revoke("test-jti", "display", Instant.now().plusSeconds(60)));
        assertNull(read.getCause());
        assertNull(write.getCause());
        assertEquals("JWT_REVOCATION_UNAVAILABLE", read.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void enabledTemplateLoaderFailureIsSafeAndCannotAuthenticateThroughHealthyCatalog() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenThrow(new IllegalStateException(PRIVATE_ERROR));
        var store = store(provider, true);
        var auth = auth(store);
        String token = healthyToken();
        var error = assertThrows(JwtRevocationUnavailableException.class, () -> store.isRevoked("test-jti"));
        assertNull(error.getCause());
        assertEquals("JWT_REVOCATION_UNAVAILABLE", error.getMessage());
        assertTrue(auth.verifyAuthorization(token, "admin").isEmpty());
        assertFalse(auth.revokeAuthorization(token));
        assertEquals("登录服务暂不可用，请稍后重试", assertThrows(AdminUserAuthenticationException.class,
                () -> auth.issueToken(account())).getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void explicitlyDisabledJdbcKeepsTheExistingModeWithoutAccessingTemplate() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        var store = store(provider, false);
        var auth = auth(store);
        String token = auth.issueToken(account());
        assertFalse(store.available());
        assertFalse(store.isRevoked("test-jti"));
        store.revoke("test-jti", "display", Instant.now().plusSeconds(60));
        assertTrue(auth.verifyAuthorization(token, "admin").isPresent());
        assertTrue(auth.revokeAuthorization(token));
        verifyNoInteractions(provider);
    }

    @Test
    void jdbcDataAccessReadAndWriteExceptionsLoseDriverDetailsAcrossRepositoryAuthAndLogs() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object.class)))
                .thenThrow(new DataAccessResourceFailureException(PRIVATE_ERROR));
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException(PRIVATE_ERROR));
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("mysqlJdbcTemplate", jdbc);
        var store = store(beans.getBeanProvider(JdbcTemplate.class), true);
        var auth = auth(store);
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        var repositoryLogger = (Logger) LoggerFactory.getLogger(JwtRevocationRepository.class);
        var authLogger = (Logger) LoggerFactory.getLogger(AdminAuthService.class);
        repositoryLogger.addAppender(captured);
        authLogger.addAppender(captured);
        try {
            var read = assertThrows(JwtRevocationUnavailableException.class, () -> store.isRevoked("test-jti"));
            var write = assertThrows(JwtRevocationUnavailableException.class,
                    () -> store.revoke("test-jti", "display", Instant.now().plusSeconds(60)));
            assertNull(read.getCause());
            assertNull(write.getCause());
            String token = healthyToken();
            assertTrue(auth.verifyAuthorization(token, "admin").isEmpty());
            assertFalse(auth.revokeAuthorization(token));
            assertFalse(captured.list.isEmpty());
            for (var event : captured.list) {
                assertFalse(event.getFormattedMessage().contains("private-driver-marker"));
                assertFalse(event.getFormattedMessage().contains("13800138000"));
                assertFalse(event.getFormattedMessage().contains("StrongPass1"));
                assertNull(event.getThrowableProxy());
            }
        } finally {
            repositoryLogger.detachAppender(captured);
            authLogger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void logoutWriteFailureReturnsExplicitSafeFailureEnvelopeWithoutHttp500() throws Exception {
        var port = mock(JwtRevocationPort.class);
        when(port.available()).thenReturn(true);
        doThrow(new DataAccessResourceFailureException(PRIVATE_ERROR)).when(port).revoke(anyString(), anyString(), any());
        var auth = auth(port);
        var service = mock(AdminUserApplicationService.class);
        when(service.logout(anyString())).thenAnswer(invocation -> auth.revokeAuthorization(invocation.getArgument(0)));
        String body = mvc(service, auth).perform(post("/api/v1/admin/admin-user/logout")
                .header("Authorization", "Bearer " + auth.issueToken(account()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var result = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        assertEquals("0001", result.get("code").asText());
        assertFalse(result.get("data").asBoolean());
        assertEquals("注销未完成，请稍后重试", result.get("info").asText());
        assertFalse(body.contains("private-driver-marker"));
        assertFalse(body.contains("13800138000"));
        assertFalse(body.contains("StrongPass1"));
    }

    @Test
    void revocationReadFailureRejectsProtectedHttpRequestInsteadOfThrowing500() throws Exception {
        var port = mock(JwtRevocationPort.class);
        when(port.available()).thenReturn(true);
        when(port.isRevoked(anyString())).thenThrow(new DataAccessResourceFailureException(PRIVATE_ERROR));
        var auth = auth(port);
        var service = mock(AdminUserApplicationService.class);
        String body = mvc(service, auth).perform(post("/api/v1/admin/admin-user/logout")
                .header("Authorization", "Bearer " + healthyToken())).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        verifyNoInteractions(service);
        assertFalse(body.contains("private-driver-marker"));
        assertFalse(body.contains("13800138000"));
        assertFalse(body.contains("StrongPass1"));
    }

    @Test
    void normalLogoutKeepsTheExistingSuccessEnvelopeAndDoesNotAcceptMissingStore() throws Exception {
        var port = mock(JwtRevocationPort.class);
        when(port.available()).thenReturn(true);
        var auth = auth(port);
        var service = mock(AdminUserApplicationService.class);
        when(service.logout(anyString())).thenAnswer(invocation -> auth.revokeAuthorization(invocation.getArgument(0)));
        String body = mvc(service, auth).perform(post("/api/v1/admin/admin-user/logout")
                .header("Authorization", "Bearer " + auth.issueToken(account()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var result = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        assertEquals("0000", result.get("code").asText());
        assertTrue(result.get("data").asBoolean());
        verify(port).revoke(anyString(), eq("display"), any());
        var noStore = auth(null);
        String token = healthyToken();
        assertTrue(noStore.verifyAuthorization(token, "admin").isEmpty());
        assertFalse(noStore.revokeAuthorization(token));
        assertThrows(AdminUserAuthenticationException.class, () -> noStore.issueToken(account()));
    }

    private JwtRevocationRepository store(ObjectProvider<JdbcTemplate> provider, boolean enabled) {
        var store = new JwtRevocationRepository(provider);
        ReflectionTestUtils.setField(store, "jdbcEnabled", enabled);
        ReflectionTestUtils.setField(store, "autoInit", false);
        return store;
    }

    private AdminAuthService auth(JwtRevocationPort port) {
        var catalog = mock(AdminUserCatalogPort.class);
        when(catalog.findByUserId("owner-stable")).thenReturn(account());
        return new AdminAuthService(port, new AdminAuthSettings(
                "synthetic-test-signing-material-for-revocation-failure", 1L, "", true), catalog);
    }

    private String healthyToken() {
        return auth(mock(JwtRevocationPort.class)).issueToken(account());
    }

    private MockMvc mvc(AdminUserApplicationService service, AdminAuthService auth) {
        var limiter = mock(OpsRateLimitService.class);
        when(limiter.tryAcquire(anyString(), anyString())).thenReturn(true);
        return MockMvcBuilders.standaloneSetup(new AdminUserAdminController(service))
                .addInterceptors(new AdminWebSecurityConfig.AdminAuthInterceptor(auth, limiter)).build();
    }

    private AdminUserAccount account() {
        return new AdminUserAccount(1L, "owner-stable", "display", "encoded", AdminUserRole.ADMIN,
                1, null, null, "13800138000");
    }
}
