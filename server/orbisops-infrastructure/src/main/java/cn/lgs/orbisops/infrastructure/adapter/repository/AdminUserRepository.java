package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.security.AdminUserCatalogPort;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.infrastructure.dao.IAdminUserDao;
import cn.lgs.orbisops.infrastructure.dao.po.AdminUser;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JDBC/MyBatis-backed adapter for the Security Identity account catalog.
 *
 * <p>Persistence records remain inside Infrastructure; the Application boundary exposes only the
 * Security Identity aggregate. OrbisOps never synthesizes a built-in administrator or a default
 * password when persistence is unavailable.</p>
 */
@Repository
public class AdminUserRepository implements AdminUserCatalogPort {

    private final IAdminUserDao dao;

    public AdminUserRepository(IAdminUserDao dao) {
        this.dao = dao;
    }

    @Override
    public boolean insert(AdminUserAccount account) {
        return dao.insert(toPo(account)) > 0;
    }

    @Override
    public boolean updateById(AdminUserAccount account) {
        return dao.updateById(toPo(account)) > 0;
    }

    @Override
    public boolean updateByUserId(AdminUserAccount account) {
        return dao.updateByUserId(toPo(account)) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return dao.deleteById(id) > 0;
    }

    @Override
    public boolean deleteByUserId(String userId) {
        return dao.deleteByUserId(userId) > 0;
    }

    @Override
    public AdminUserAccount findById(Long id) {
        return toAccount(dao.queryById(id));
    }

    @Override
    public AdminUserAccount findByUserId(String userId) {
        return toAccount(dao.queryByUserId(userId));
    }

    @Override
    public AdminUserAccount findByUsername(String username) {
        return toAccount(dao.queryByUsername(username));
    }

    @Override
    public List<AdminUserAccount> listEnabled() {
        return toAccounts(dao.queryEnabledUsers());
    }

    @Override
    public List<AdminUserAccount> listByStatus(Integer status) {
        return toAccounts(dao.queryByStatus(status));
    }

    @Override
    public List<AdminUserAccount> listAll() {
        return toAccounts(dao.queryAll());
    }

    private AdminUser toPo(AdminUserAccount account) {
        if (account == null) {
            throw new IllegalArgumentException("ADMIN_USER_ACCOUNT_REQUIRED");
        }
        return AdminUser.builder()
                .id(account.id())
                .userId(account.userId())
                .username(account.username())
                .password(account.credential())
                .userRole(account.role() == null ? null : account.role().value())
                .status(account.status())
                .createTime(account.createTime())
                .updateTime(account.updateTime())
                .build();
    }

    private AdminUserAccount toAccount(AdminUser po) {
        if (po == null) {
            return null;
        }
        return new AdminUserAccount(
                po.getId(),
                po.getUserId(),
                po.getUsername(),
                po.getPassword(),
                AdminUserRole.parse(po.getUserRole(), AdminUserRole.ADMIN),
                po.getStatus(),
                po.getCreateTime(),
                po.getUpdateTime());
    }

    private List<AdminUserAccount> toAccounts(List<AdminUser> rows) {
        return rows == null || rows.isEmpty()
                ? List.of()
                : rows.stream().map(this::toAccount).toList();
    }
}
