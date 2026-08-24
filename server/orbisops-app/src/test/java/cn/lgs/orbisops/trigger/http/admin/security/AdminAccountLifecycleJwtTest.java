package cn.lgs.orbisops.trigger.http.admin.security;

import cn.lgs.orbisops.application.security.AdminUserCatalogPort;
import cn.lgs.orbisops.application.security.JwtRevocationPort;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthSettings;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAccountLifecycleJwtTest {
    private static final String SIGNING = "synthetic-test-signing-material-for-account-lifecycle";

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void disablingOrLockingInvalidatesAnAlreadyIssuedJwtOnTheNextRequest(int status) {
        var catalog = mock(AdminUserCatalogPort.class);
        var auth = auth(catalog);
        var original = account(AdminUserRole.ADMIN, 1, "display");
        when(catalog.findByUserId("immutable-owner")).thenReturn(original);
        String token = auth.issueToken(original);
        assertTrue(auth.verifyAuthorization(token).isPresent());
        when(catalog.findByUserId("immutable-owner")).thenReturn(account(AdminUserRole.ADMIN, status, "display"));
        assertTrue(auth.verifyAuthorization(token, "admin", "user").isEmpty());
    }

    @Test
    void roleDowngradeCannotRetainAdminScopeAndRoleUpgradeCannotReuseOldUserJwt() {
        var catalog = mock(AdminUserCatalogPort.class);
        var auth = auth(catalog);
        String oldAdmin = auth.issueToken(account(AdminUserRole.ADMIN, 1, "display"));
        String oldUser = auth.issueToken(account(AdminUserRole.USER, 1, "display"));
        when(catalog.findByUserId("immutable-owner")).thenReturn(account(AdminUserRole.USER, 1, "display"));
        assertTrue(auth.verifyAuthorization(oldAdmin, "admin", "user").isEmpty());
        assertTrue(auth.verifyAuthorization(oldUser, "user").isPresent());
        when(catalog.findByUserId("immutable-owner")).thenReturn(account(AdminUserRole.ADMIN, 1, "display"));
        assertTrue(auth.verifyAuthorization(oldUser, "admin", "user").isEmpty());
    }

    @Test
    void deletedMissingAndUnavailableAccountCatalogFailClosed() {
        var catalog = mock(AdminUserCatalogPort.class);
        var auth = auth(catalog);
        String token = auth.issueToken(account(AdminUserRole.ADMIN, 1, "display"));
        assertTrue(auth.verifyAuthorization(token).isEmpty());
        when(catalog.findByUserId("immutable-owner")).thenThrow(new IllegalStateException("private driver error"));
        assertTrue(auth.verifyAuthorization(token).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new AdminAuthService(mock(JwtRevocationPort.class), settings(), null));
    }

    @Test
    void displayOrPhoneChangesKeepOwnerIdentityAndReturnCurrentDisplayName() {
        var catalog = mock(AdminUserCatalogPort.class);
        var auth = auth(catalog);
        String token = auth.issueToken(account(AdminUserRole.USER, 1, "old-display"));
        when(catalog.findByUserId("immutable-owner")).thenReturn(new AdminUserAccount(1L,
                "immutable-owner", "new-display", "encoded", AdminUserRole.USER, 1,
                null, null, "13900139000"));
        var principal = auth.verifyAuthorization(token, "user").orElseThrow();
        assertEquals("immutable-owner", principal.userId());
        assertEquals("new-display", principal.username());
        verify(catalog).findByUserId("immutable-owner");
        verify(catalog, never()).findByUsername(any());
        verify(catalog, never()).findByPhone(any());
    }

    @Test
    void signedTokenMissingImmutableUserIdCannotAuthenticateThroughDisplayName() {
        var catalog = mock(AdminUserCatalogPort.class);
        var auth = auth(catalog);
        String token = JWT.create().withIssuer("orbisops").withSubject("display").withClaim("scope", "admin")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000)).sign(Algorithm.HMAC256(SIGNING));
        assertTrue(auth.verifyAuthorization(token).isEmpty());
        verifyNoInteractions(catalog);
    }

    private AdminAuthService auth(AdminUserCatalogPort catalog) {
        return new AdminAuthService(mock(JwtRevocationPort.class), settings(), catalog);
    }

    private AdminAuthSettings settings() {
        return new AdminAuthSettings(SIGNING, 1L, "", true);
    }

    private AdminUserAccount account(AdminUserRole role, int status, String display) {
        return new AdminUserAccount(1L, "immutable-owner", display, "encoded", role,
                status, null, null, "13800138000");
    }
}
