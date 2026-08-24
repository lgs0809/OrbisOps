package cn.lgs.orbisops.application.security;

import cn.lgs.orbisops.domain.security.AdminCredentialRules;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminPhoneIdentityUseCasesTest {
    @Test
    void phoneCreationGeneratesStableIdentityAndDefaultsToUserWithoutStoringRawPassword() {
        var catalog = mock(AdminUserCatalogPort.class);
        var authentication = mock(AdminUserAuthenticationPort.class);
        when(authentication.encodeForStorage("StrongPass1")).thenReturn("encoded-value");
        when(catalog.insert(any())).thenReturn(true);
        var useCase = new AdminUserCatalogUseCase(catalog, authentication, new AdminCredentialRules(8, false));
        assertTrue(useCase.create(new AdminUserCommand(null, null, " 值班同事 ", "StrongPass1", null, null,
                "+86 13800138000")));
        var saved = ArgumentCaptor.forClass(AdminUserAccount.class);
        verify(catalog).insert(saved.capture());
        assertTrue(saved.getValue().userId().startsWith("user-"));
        assertEquals("13800138000", saved.getValue().phone());
        assertEquals("值班同事", saved.getValue().username());
        assertEquals(AdminUserRole.USER, saved.getValue().role());
        assertEquals("encoded-value", saved.getValue().credential());
        assertFalse(saved.getValue().toString().contains("13800138000"));
        assertFalse(saved.getValue().toString().contains("encoded-value"));
    }

    @Test
    void duplicatePhoneIsRejectedBeforeEncodingOrInsertingAnotherUser() {
        var catalog = mock(AdminUserCatalogPort.class);
        var authentication = mock(AdminUserAuthenticationPort.class);
        when(catalog.findByPhone("13800138000")).thenReturn(account("existing", 1));
        var useCase = new AdminUserCatalogUseCase(catalog, authentication, new AdminCredentialRules(8, false));
        var error = assertThrows(IllegalArgumentException.class, () -> useCase.create(new AdminUserCommand(
                null, null, "another", "StrongPass1", "user", 1, "8613800138000")));
        assertEquals("手机号已被使用", error.getMessage());
        verify(authentication, never()).encodeForStorage(any());
        verify(catalog, never()).insert(any());
    }

    @Test
    void updateCanRetainOwnPhoneButCannotReassignOwnerIdentity() {
        var catalog = mock(AdminUserCatalogPort.class);
        var owner = account("existing", 1);
        when(catalog.findById(1L)).thenReturn(owner);
        when(catalog.findByPhone("13800138000")).thenReturn(owner);
        when(catalog.updateById(any())).thenReturn(true);
        var useCase = new AdminUserCatalogUseCase(catalog, mock(AdminUserAuthenticationPort.class),
                new AdminCredentialRules(8, false));
        assertTrue(useCase.updateById(new AdminUserCommand(1L, "existing", "新名称", null, "user", 1,
                "+8613800138000")));
        assertEquals("用户ID不可修改", assertThrows(IllegalArgumentException.class,
                () -> useCase.updateById(new AdminUserCommand(1L, "replacement", "新名称", null,
                        "user", 1, null))).getMessage());
        verify(catalog, times(1)).updateById(any());
    }

    @Test
    void phoneLoginHasPriorityAndLegacyCredentialMigrationKeepsPhoneAndOwner() {
        var catalog = mock(AdminUserCatalogPort.class);
        var authentication = mock(AdminUserAuthenticationPort.class);
        var owner = account("existing", 1);
        when(catalog.findByPhone("13800138000")).thenReturn(owner);
        when(authentication.matches("StrongPass1", "legacy-secret")).thenReturn(true);
        when(authentication.encodeForStorage("StrongPass1")).thenReturn("encoded-value");
        when(authentication.issueToken(any())).thenReturn("opaque-token");
        var useCase = new AdminUserAuthenticationUseCase(catalog, authentication);
        var result = useCase.login(new AdminUserLoginCommand("wrong-legacy-name", "StrongPass1", "+8613800138000"));
        assertEquals("existing", result.account().userId());
        assertEquals("13800138000", result.account().phone());
        assertEquals("opaque-token", result.token());
        verify(catalog, never()).findByUsername(any());
        verify(catalog).updateById(result.account());
    }

    @Test
    void phoneLoginRejectsWrongPasswordDisabledLockedAndMalformedWithoutTokens() {
        var catalog = mock(AdminUserCatalogPort.class);
        var authentication = mock(AdminUserAuthenticationPort.class);
        var useCase = new AdminUserAuthenticationUseCase(catalog, authentication);
        var command = new AdminUserLoginCommand(null, "StrongPass1", "13800138000");
        assertEquals("手机号或密码错误", assertThrows(AdminUserAuthenticationException.class,
                () -> useCase.login(command)).getMessage());
        when(catalog.findByPhone("13800138000")).thenReturn(account("existing", 0));
        when(authentication.matches("StrongPass1", "legacy-secret")).thenReturn(true);
        assertEquals("用户已被禁用", assertThrows(AdminUserAuthenticationException.class,
                () -> useCase.login(command)).getMessage());
        when(catalog.findByPhone("13800138000")).thenReturn(account("existing", 2));
        assertEquals("用户已被锁定", assertThrows(AdminUserAuthenticationException.class,
                () -> useCase.login(command)).getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> useCase.login(new AdminUserLoginCommand("existing", "StrongPass1", "bad-phone")));
        verify(authentication, never()).issueToken(any());
        verify(catalog, never()).findByUsername(any());
    }

    @Test
    void bootstrapUsesPhoneButNeverReopensForAnExistingAccount() {
        var catalog = mock(AdminUserCatalogUseCase.class);
        var authentication = mock(AdminUserAuthenticationUseCase.class);
        when(catalog.listAll()).thenReturn(List.of());
        when(catalog.create(any())).thenReturn(true);
        var useCase = new FirstTimeSetupUseCase(catalog, authentication);
        useCase.setup("first-admin", "StrongPass1", "13800138000");
        var created = ArgumentCaptor.forClass(AdminUserCommand.class);
        verify(catalog).create(created.capture());
        assertEquals("admin", created.getValue().userRole());
        assertEquals("13800138000", created.getValue().phone());
        var login = ArgumentCaptor.forClass(AdminUserLoginCommand.class);
        verify(authentication).login(login.capture());
        assertEquals("13800138000", login.getValue().phone());
        when(catalog.listAll()).thenReturn(List.of(account("existing", 1)));
        assertThrows(IllegalStateException.class,
                () -> useCase.setup("second-admin", "StrongPass1", "13900139000"));
        verify(catalog, times(1)).create(any());
    }

    @Test
    void accountCommandsNeverPrintSubmittedPhonesOrPasswords() {
        var command = new AdminUserCommand(null, "u-test", "display", "StrongPass1", "user", 1, "13800138000");
        var login = new AdminUserLoginCommand("display", "StrongPass1", "13800138000");
        for (var value : List.of(command.toString(), login.toString())) {
            assertFalse(value.contains("StrongPass1"));
            assertFalse(value.contains("13800138000"));
        }
    }

    private AdminUserAccount account(String userId, int status) {
        return new AdminUserAccount(1L, userId, "display", "legacy-secret", AdminUserRole.USER,
                status, null, null, "13800138000");
    }
}
