package cn.lgs.orbisops.trigger.application.security;

import cn.lgs.orbisops.domain.agent.adapter.repository.IAdminUserRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AdminUserRecord;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAdminUserBoundaryAdaptersTest {

    @Test
    void catalogAdapterMapsAccountAndRepositoryRecord() {
        IAdminUserRepository repository = mock(IAdminUserRepository.class);
        OpsAdminUserCatalogAdapter adapter = new OpsAdminUserCatalogAdapter(repository);
        when(repository.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        AdminUserAccount account = account();

        assertTrue(adapter.insert(account));

        ArgumentCaptor<AdminUserRecord> record = ArgumentCaptor.forClass(AdminUserRecord.class);
        verify(repository).insert(record.capture());
        assertEquals("u-10001", record.getValue().getUserId());
        assertEquals("admin", record.getValue().getUserRole());
        assertEquals("encoded-value", record.getValue().getPassword());
    }

    @Test
    void catalogAdapterDefaultsMissingStoredRoleToAdmin() {
        IAdminUserRepository repository = mock(IAdminUserRepository.class);
        OpsAdminUserCatalogAdapter adapter = new OpsAdminUserCatalogAdapter(repository);
        when(repository.queryAll()).thenReturn(List.of(AdminUserRecord.builder()
                .id(7L)
                .userId("u-10001")
                .username("ops-admin")
                .password("encoded-value")
                .status(1)
                .build()));

        List<AdminUserAccount> result = adapter.listAll();

        assertEquals(AdminUserRole.ADMIN, result.get(0).role());
    }

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

        ArgumentCaptor<AdminUserRecord> record = ArgumentCaptor.forClass(AdminUserRecord.class);
        verify(authentication).issueToken(record.capture());
        assertEquals("admin", record.getValue().getUserRole());
        assertEquals("u-10001", record.getValue().getUserId());
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
