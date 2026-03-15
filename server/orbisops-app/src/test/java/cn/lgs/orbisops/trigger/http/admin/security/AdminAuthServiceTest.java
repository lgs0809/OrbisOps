package cn.lgs.orbisops.trigger.http.admin.security;

import cn.lgs.orbisops.application.security.JwtRevocationPort;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthSettings;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

        String authorization = authService.issueToken(account("10001", "admin", AdminUserRole.ADMIN));

        Optional<AdminAuthService.AuthPrincipal> principal = authService.verifyAuthorization("Bearer " + authorization);

        assertTrue(principal.isPresent());
        assertEquals("admin", principal.get().username());
        assertEquals("10001", principal.get().userId());
        assertEquals("admin", principal.get().scope());
        assertTrue(authService.verifyAuthorization(authorization).isPresent());
        assertTrue(authService.verifyAuthorization("bad-token").isEmpty());
    }

    @Test
    public void shouldTreatMissingTypedRoleAsUserCompatibilityScope() {
        AdminAuthService authService = authService(false);

        String authorization = authService.issueToken(account("20003", "legacy-user", null));

        assertTrue(authService.verifyAuthorization("Bearer " + authorization, AdminAuthService.SCOPE_USER).isPresent());
        assertTrue(authService.verifyAuthorization("Bearer " + authorization, AdminAuthService.SCOPE_ADMIN).isEmpty());
    }

    @Test
    public void shouldAllowUserJwtOnlyForAgentScope() {
        AdminAuthService authService = authService(false);

        String authorization = authService.issueToken(account("20001", "ops-user", AdminUserRole.USER));

        assertTrue(authService.verifyAuthorization("Bearer " + authorization, AdminAuthService.SCOPE_USER).isPresent());
        assertTrue(authService.verifyAuthorization("Bearer " + authorization, AdminAuthService.SCOPE_ADMIN, AdminAuthService.SCOPE_USER).isPresent());
        assertTrue(authService.verifyAuthorization("Bearer " + authorization, AdminAuthService.SCOPE_ADMIN).isEmpty());
    }

    @Test
    public void unknownAccountRoleMustBeRejectedBeforeTokenIssuance() {
        assertThrows(IllegalArgumentException.class, () -> AdminUserRole.parse("guest", AdminUserRole.USER));
    }

    @Test
    public void shouldAcceptConfiguredServiceCredentialOnly() {
        AdminAuthService authService = authService(false);

        assertTrue(authService.verifyServiceToken(serviceCredential()));
        assertFalse(authService.verifyServiceToken("legacy-credential"));
        assertFalse(authService.verifyServiceToken("wrong-credential"));
    }

    @Test
    public void shouldRejectRevokedJwtWhenRevocationStoreMarksItRevoked() {
        JwtRevocationPort repository = mock(JwtRevocationPort.class);
        when(repository.available()).thenReturn(true);
        when(repository.isRevoked(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        AdminAuthService authService = new AdminAuthService(
                repository,
                new AdminAuthSettings(signingMaterial(), 2L, serviceCredential(), false));
        String authorization = authService.issueToken(account("10001", "admin", AdminUserRole.ADMIN));

        assertTrue(authService.revokeAuthorization("Bearer " + authorization));
        assertTrue(authService.verifyAuthorization("Bearer " + authorization).isEmpty());
    }

    @Test
    public void shouldRejectWeakJwtSigningMaterialWhenConfigured() {
        AdminAuthService authService = new AdminAuthService(
                mock(JwtRevocationPort.class),
                new AdminAuthSettings("short", 2L, "", true));

        assertThrows(IllegalStateException.class, authService::validateJwtSecret);
    }

    private AdminAuthService authService(boolean revocationEnabled) {
        JwtRevocationPort repository = mock(JwtRevocationPort.class);
        when(repository.available()).thenReturn(revocationEnabled);
        when(repository.isRevoked(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        return new AdminAuthService(
                repository,
                new AdminAuthSettings(signingMaterial(), 2L, serviceCredential(), false));
    }

    private AdminUserAccount account(String userId, String username, AdminUserRole role) {
        return new AdminUserAccount(null, userId, username, null, role, 1, null, null);
    }

    private String signingMaterial() {
        return "k".repeat(64);
    }

    private String serviceCredential() {
        return "service-" + "credential";
    }
}
