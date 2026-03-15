package cn.lgs.orbisops.application.security;

import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserAuthenticationUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T07:40:00Z"),
            ZoneOffset.UTC);

    @Test
    void loginMigratesLegacyCredentialBeforeIssuingToken() {
        AdminUserCatalogPort catalog = mock(AdminUserCatalogPort.class);
        AdminUserAuthenticationPort authentication = mock(AdminUserAuthenticationPort.class);
        AdminUserAuthenticationUseCase useCase = new AdminUserAuthenticationUseCase(
                catalog, authentication, CLOCK);
        AdminUserAccount legacy = account("legacy-value", 1);
        when(catalog.findByUsername("ops-admin")).thenReturn(legacy);
        when(authentication.matches("StrongPass1", "legacy-value")).thenReturn(true);
        when(authentication.isEncoded("legacy-value")).thenReturn(false);
        when(authentication.encodeForStorage("StrongPass1")).thenReturn("encoded-value");
        when(authentication.issueToken(org.mockito.ArgumentMatchers.any())).thenReturn("jwt-token");

        AdminUserLoginResult result = useCase.login(new AdminUserLoginCommand("ops-admin", "StrongPass1"));

        ArgumentCaptor<AdminUserAccount> migrated = ArgumentCaptor.forClass(AdminUserAccount.class);
        InOrder order = inOrder(catalog, authentication);
        order.verify(catalog).findByUsername("ops-admin");
        order.verify(authentication).matches("StrongPass1", "legacy-value");
        order.verify(authentication).isEncoded("legacy-value");
        order.verify(authentication).encodeForStorage("StrongPass1");
        order.verify(catalog).updateById(migrated.capture());
        order.verify(authentication).issueToken(migrated.getValue());
        assertEquals("encoded-value", migrated.getValue().credential());
        assertEquals(LocalDateTime.of(2026, 7, 30, 7, 40), migrated.getValue().updateTime());
        assertEquals("jwt-token", result.token());
    }

    @Test
    void encodedCredentialSkipsMigration() {
        AdminUserCatalogPort catalog = mock(AdminUserCatalogPort.class);
        AdminUserAuthenticationPort authentication = mock(AdminUserAuthenticationPort.class);
        AdminUserAuthenticationUseCase useCase = new AdminUserAuthenticationUseCase(
                catalog, authentication, CLOCK);
        AdminUserAccount account = account("encoded-value", 1);
        when(catalog.findByUsername("ops-admin")).thenReturn(account);
        when(authentication.matches("StrongPass1", "encoded-value")).thenReturn(true);
        when(authentication.isEncoded("encoded-value")).thenReturn(true);
        when(authentication.issueToken(account)).thenReturn("jwt-token");

        AdminUserLoginResult result = useCase.login(new AdminUserLoginCommand("ops-admin", "StrongPass1"));

        assertEquals(account, result.account());
        verify(catalog, never()).updateById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsWrongCredentialDisabledAndLockedAccounts() {
        AdminUserCatalogPort catalog = mock(AdminUserCatalogPort.class);
        AdminUserAuthenticationPort authentication = mock(AdminUserAuthenticationPort.class);
        AdminUserAuthenticationUseCase useCase = new AdminUserAuthenticationUseCase(
                catalog, authentication, CLOCK);
        when(catalog.findByUsername("missing")).thenReturn(null);

        AdminUserAuthenticationException wrong = assertThrows(AdminUserAuthenticationException.class,
                () -> useCase.login(new AdminUserLoginCommand("missing", "StrongPass1")));
        assertEquals("用户名或密码错误", wrong.getMessage());

        AdminUserAccount disabled = account("encoded", 0);
        when(catalog.findByUsername("disabled")).thenReturn(disabled);
        when(authentication.matches("StrongPass1", "encoded")).thenReturn(true);
        AdminUserAuthenticationException disabledError = assertThrows(AdminUserAuthenticationException.class,
                () -> useCase.login(new AdminUserLoginCommand("disabled", "StrongPass1")));
        assertEquals("用户已被禁用", disabledError.getMessage());

        AdminUserAccount locked = account("encoded", 2);
        when(catalog.findByUsername("locked")).thenReturn(locked);
        AdminUserAuthenticationException lockedError = assertThrows(AdminUserAuthenticationException.class,
                () -> useCase.login(new AdminUserLoginCommand("locked", "StrongPass1")));
        assertEquals("用户已被锁定", lockedError.getMessage());
    }

    @Test
    void logoutDelegatesToAuthenticationProtocol() {
        AdminUserAuthenticationPort authentication = mock(AdminUserAuthenticationPort.class);
        AdminUserAuthenticationUseCase useCase = new AdminUserAuthenticationUseCase(
                mock(AdminUserCatalogPort.class), authentication, CLOCK);
        when(authentication.revokeAuthorization("Bearer token")).thenReturn(true);

        assertTrue(useCase.logout("Bearer token"));

        verify(authentication).revokeAuthorization("Bearer token");
    }

    private AdminUserAccount account(String credential, Integer status) {
        return new AdminUserAccount(
                7L,
                "u-10001",
                "ops-admin",
                credential,
                AdminUserRole.ADMIN,
                status,
                LocalDateTime.of(2026, 7, 29, 7, 0),
                LocalDateTime.of(2026, 7, 29, 7, 30));
    }
}
