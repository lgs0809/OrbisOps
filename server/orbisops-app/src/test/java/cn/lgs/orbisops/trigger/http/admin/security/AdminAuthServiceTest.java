package cn.lgs.orbisops.trigger.http.admin.security;

import cn.lgs.orbisops.domain.agent.adapter.repository.IJwtRevocationRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AdminUserRecord;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthSettings;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminAuthServiceTest {

    @Test
    public void shouldHashPasswordsAndKeepLegacyPlaintextCompatible() {
        AdminAuthService authService = authService(false);

        String encoded = authService.hashPasswordForStorage("123456");

        assertTrue(authService.isBcrypt(encoded));
        assertTrue(authService.passwordMatches("123456", encoded));
        assertFalse(authService.passwordMatches("wrong", encoded));
        assertTrue(authService.passwordMatches("legacy-password", "legacy-password"));
        assertFalse(authService.passwordMatches("legacy-password", "another-password"));
    }

    @Test
    public void shouldIssueAndVerifyAdminJwt() {
        AdminAuthService authService = authService(false);

        String token = authService.issueToken(AdminUserRecord.builder()
                .userId("10001")
                .username("admin")
                .userRole("admin")
                .build());

        Optional<AdminAuthService.AuthPrincipal> principal = authService.verifyAuthorization("Bearer " + token);

        assertTrue(principal.isPresent());
        assertEquals("admin", principal.get().username());
        assertEquals("10001", principal.get().userId());
        assertEquals("admin", principal.get().scope());
        assertTrue(authService.verifyAuthorization(token).isPresent());
        assertTrue(authService.verifyAuthorization("bad-token").isEmpty());
    }

    @Test
    public void shouldNotTreatMissingRoleAsAdmin() {
        AdminAuthService authService = authService(false);

        String token = authService.issueToken(AdminUserRecord.builder()
                .userId("20003")
                .username("legacy-user")
                .build());

        assertTrue(authService.verifyAuthorization("Bearer " + token, AdminAuthService.SCOPE_USER).isPresent());
        assertTrue(authService.verifyAuthorization("Bearer " + token, AdminAuthService.SCOPE_ADMIN).isEmpty());
    }

    @Test
    public void shouldAllowUserJwtOnlyForAgentScope() {
        AdminAuthService authService = authService(false);

        String token = authService.issueToken(AdminUserRecord.builder()
                .userId("20001")
                .username("ops-user")
                .userRole("user")
                .build());

        assertTrue(authService.verifyAuthorization("Bearer " + token, AdminAuthService.SCOPE_USER).isPresent());
        assertTrue(authService.verifyAuthorization("Bearer " + token, AdminAuthService.SCOPE_ADMIN, AdminAuthService.SCOPE_USER).isPresent());
        assertTrue(authService.verifyAuthorization("Bearer " + token, AdminAuthService.SCOPE_ADMIN).isEmpty());
    }

    @Test
    public void shouldRejectUnknownJwtScope() {
        AdminAuthService authService = authService(false);

        String token = authService.issueToken(AdminUserRecord.builder()
                .userId("20002")
                .username("ops-guest")
                .userRole("guest")
                .build());

        assertTrue(authService.verifyAuthorization("Bearer " + token, AdminAuthService.SCOPE_ADMIN).isEmpty());
        assertTrue(authService.verifyAuthorization("Bearer " + token, AdminAuthService.SCOPE_USER).isEmpty());
    }

    @Test
    public void shouldAcceptConfiguredServiceTokenOnly() {
        AdminAuthService authService = authService(false);

        assertTrue(authService.verifyServiceToken("service-token"));
        assertFalse(authService.verifyServiceToken("legacy-token"));
        assertFalse(authService.verifyServiceToken("wrong-token"));
    }

    @Test
    public void shouldRejectRevokedJwtWhenRevocationStoreMarksItRevoked() {
        IJwtRevocationRepository repository = mock(IJwtRevocationRepository.class);
        when(repository.available()).thenReturn(true);
        when(repository.isRevoked(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        AdminAuthService authService = new AdminAuthService(
                repository,
                new AdminAuthSettings("unit-test-key-material-that-is-long-enough-for-hmac256", 2L, "service-token", false));
        String token = authService.issueToken(AdminUserRecord.builder()
                .userId("10001")
                .username("admin")
                .build());

        assertTrue(authService.revokeAuthorization("Bearer " + token));
        assertTrue(authService.verifyAuthorization("Bearer " + token).isEmpty());
    }

    @Test
    public void shouldRejectWeakJwtSecretWhenConfigured() {
        AdminAuthService authService = new AdminAuthService(
                mock(IJwtRevocationRepository.class),
                new AdminAuthSettings("dev-only-change-me", 2L, "", true));

        assertThrows(IllegalStateException.class, authService::validateJwtSecret);
    }

    private AdminAuthService authService(boolean revocationEnabled) {
        IJwtRevocationRepository repository = mock(IJwtRevocationRepository.class);
        when(repository.available()).thenReturn(revocationEnabled);
        when(repository.isRevoked(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        return new AdminAuthService(
                repository,
                new AdminAuthSettings("unit-test-key-material-that-is-long-enough-for-hmac256", 2L, "service-token", false));
    }

}
