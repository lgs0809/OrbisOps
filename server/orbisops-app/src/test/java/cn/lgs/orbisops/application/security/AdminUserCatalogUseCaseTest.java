package cn.lgs.orbisops.application.security;

import cn.lgs.orbisops.domain.security.AdminCredentialRules;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserCatalogUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T07:30:00Z"),
            ZoneOffset.UTC);

    @Test
    void createNormalizesRoleEncodesCredentialDefaultsStatusAndTimes() {
        AdminUserCatalogPort catalog = mock(AdminUserCatalogPort.class);
        AdminUserAuthenticationPort authentication = mock(AdminUserAuthenticationPort.class);
        AdminUserCatalogUseCase useCase = new AdminUserCatalogUseCase(
                catalog,
                authentication,
                new AdminCredentialRules(8, false),
                CLOCK);
        when(authentication.isEncoded("StrongPass1")).thenReturn(false);
        when(authentication.encodeForStorage("StrongPass1")).thenReturn("encoded-value");
        when(catalog.insert(any())).thenReturn(true);

        assertTrue(useCase.create(command(null, "ADMIN", "StrongPass1", null)));

        ArgumentCaptor<AdminUserAccount> saved = ArgumentCaptor.forClass(AdminUserAccount.class);
        verify(catalog).insert(saved.capture());
        assertEquals(AdminUserRole.ADMIN, saved.getValue().role());
        assertEquals(1, saved.getValue().status());
        assertEquals("encoded-value", saved.getValue().credential());
        assertEquals(LocalDateTime.of(2026, 7, 30, 7, 30), saved.getValue().createTime());
        assertEquals(saved.getValue().createTime(), saved.getValue().updateTime());
    }

    @Test
    void rejectsUnknownRoleWeakCredentialPreencodedIngressAndInvalidStatus() {
        AdminUserCatalogPort catalog = mock(AdminUserCatalogPort.class);
        AdminUserAuthenticationPort authentication = mock(AdminUserAuthenticationPort.class);
        AdminUserCatalogUseCase useCase = new AdminUserCatalogUseCase(
                catalog,
                authentication,
                new AdminCredentialRules(8, false),
                CLOCK);

        IllegalArgumentException role = assertThrows(IllegalArgumentException.class,
                () -> useCase.create(command(null, "guest", "StrongPass1", null)));
        assertEquals("用户角色只允许 admin 或 user", role.getMessage());

        when(authentication.isEncoded("123456")).thenReturn(false);
        IllegalArgumentException weak = assertThrows(IllegalArgumentException.class,
                () -> useCase.updateById(command(1L, "user", "123456", null)));
        assertEquals("密码长度不能少于 8 位", weak.getMessage());

        when(authentication.isEncoded("encoded-input")).thenReturn(true);
        IllegalArgumentException encoded = assertThrows(IllegalArgumentException.class,
                () -> useCase.create(command(null, "admin", "encoded-input", null)));
        assertEquals("不允许通过接口提交已加密密码", encoded.getMessage());

        IllegalArgumentException status = assertThrows(IllegalArgumentException.class,
                () -> useCase.create(command(null, "user", "StrongPass1", 9)));
        assertEquals("用户状态只允许 0、1、2", status.getMessage());
        verify(catalog, never()).insert(any());
    }

    @Test
    void rejectsIdentifiersThatWouldExceedAccountSchemaBounds() {
        AdminUserCatalogPort catalog = mock(AdminUserCatalogPort.class);
        AdminUserAuthenticationPort authentication = mock(AdminUserAuthenticationPort.class);
        AdminUserCatalogUseCase useCase = new AdminUserCatalogUseCase(
                catalog,
                authentication,
                new AdminCredentialRules(8, false),
                CLOCK);

        String longUserId = "u-" + "x".repeat(63);
        IllegalArgumentException userId = assertThrows(IllegalArgumentException.class,
                () -> useCase.create(new AdminUserCommand(null, longUserId, "ops-user", "StrongPass1", "user", 1)));
        assertEquals("用户ID长度不能超过 64 个字符", userId.getMessage());

        String longUsername = "u".repeat(51);
        IllegalArgumentException username = assertThrows(IllegalArgumentException.class,
                () -> useCase.create(new AdminUserCommand(null, "u-10001", longUsername, "StrongPass1", "user", 1)));
        assertEquals("用户名长度不能超过 50 个字符", username.getMessage());
        verify(catalog, never()).insert(any());
    }

    @Test
    void queryFiltersAndAppliesBoundedPagination() {
        AdminUserCatalogPort catalog = mock(AdminUserCatalogPort.class);
        AdminUserCatalogUseCase useCase = new AdminUserCatalogUseCase(
                catalog,
                mock(AdminUserAuthenticationPort.class),
                new AdminCredentialRules(8, false),
                CLOCK);
        when(catalog.listAll()).thenReturn(List.of(
                account(1L, "u-1", "ops-admin", 1),
                account(2L, "u-2", "ops-user", 1),
                account(3L, "u-3", "guest", 0)));

        List<AdminUserAccount> result = useCase.query(new AdminUserCatalogQuery(
                null, "ops", 1, 2, 1));

        assertEquals(1, result.size());
        assertEquals("u-2", result.get(0).userId());
        assertThrows(UnsupportedOperationException.class, () -> result.add(result.get(0)));
    }

    private AdminUserCommand command(Long id, String role, String credential, Integer status) {
        return new AdminUserCommand(
                id,
                "u-10001",
                "ops-admin",
                credential,
                role,
                status);
    }

    private AdminUserAccount account(Long id, String userId, String username, Integer status) {
        return new AdminUserAccount(
                id,
                userId,
                username,
                "encoded",
                AdminUserRole.USER,
                status,
                null,
                null);
    }
}
