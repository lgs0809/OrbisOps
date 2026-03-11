package cn.lgs.orbisops.trigger.application.security;

import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAdminUserBoundaryAdaptersTest {

    @Test
    void authenticationAdapterDelegatesExistingAuthProtocol() {
        AdminAuthService authentication = mock(AdminAuthService.class);
        OpsAdminUserAuthenticationAdapter adapter = new OpsAdminUserAuthenticationAdapter(authentication);
        AdminUserAccount account = account();
        when(authentication.isBcrypt("encoded-value")).thenReturn(true);
        when(authentication.hashPasswordForStorage("raw-value")).thenReturn("encoded-value");
        when(authentication.passwordMatches("raw-value", "encoded-value")).thenReturn(true);
        when(authentication.issueToken(org.mockito.ArgumentMatchers.any())).thenReturn("jwt-token");
        when(authentication.revokeAuthorization("Bearer token")).thenReturn(true);

        assertTrue(adapter.isEncoded("encoded-value"));
        assertEquals("encoded-value", adapter.encodeForStorage("raw-value"));
        assertTrue(adapter.matches("raw-value", "encoded-value"));
        assertEquals("jwt-token", adapter.issueToken(account));
        assertTrue(adapter.revokeAuthorization("Bearer token"));

        verify(authentication).issueToken(account);
    }

    private AdminUserAccount account() {
        return new AdminUserAccount(
                7L,
                "u-10001",
                "ops-admin",
                "encoded-value",
                AdminUserRole.ADMIN,
                1,
                LocalDateTime.of(2026, 7, 30, 7, 0),
                LocalDateTime.of(2026, 7, 30, 7, 30));
    }
}
