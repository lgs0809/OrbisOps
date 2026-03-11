package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.infrastructure.dao.IAdminUserDao;
import cn.lgs.orbisops.infrastructure.dao.po.AdminUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserRepositoryTest {

    @Test
    void persistsSecurityAggregateAsInfrastructurePo() {
        IAdminUserDao dao = mock(IAdminUserDao.class);
        AdminUserRepository repository = new AdminUserRepository(dao);
        AdminUserAccount account = account(AdminUserRole.ADMIN);
        when(dao.insert(any(AdminUser.class))).thenReturn(1);

        assertTrue(repository.insert(account));

        ArgumentCaptor<AdminUser> row = ArgumentCaptor.forClass(AdminUser.class);
        verify(dao).insert(row.capture());
        assertEquals("u-10001", row.getValue().getUserId());
        assertEquals("ops-admin", row.getValue().getUsername());
        assertEquals("encoded-value", row.getValue().getPassword());
        assertEquals("admin", row.getValue().getUserRole());
    }

    @Test
    void restoresLegacyRowWithoutRoleAsAdminAccount() {
        IAdminUserDao dao = mock(IAdminUserDao.class);
        AdminUserRepository repository = new AdminUserRepository(dao);
        when(dao.queryAll()).thenReturn(List.of(AdminUser.builder()
                .id(7L)
                .userId("u-10001")
                .username("ops-admin")
                .password("encoded-value")
                .status(1)
                .build()));

        List<AdminUserAccount> result = repository.listAll();

        assertEquals(1, result.size());
        assertEquals(AdminUserRole.ADMIN, result.get(0).role());
        assertEquals("u-10001", result.get(0).userId());
    }

    private AdminUserAccount account(AdminUserRole role) {
        return new AdminUserAccount(
                7L,
                "u-10001",
                "ops-admin",
                "encoded-value",
                role,
                1,
                LocalDateTime.of(2026, 7, 30, 7, 0),
                LocalDateTime.of(2026, 7, 30, 7, 30));
    }
}
