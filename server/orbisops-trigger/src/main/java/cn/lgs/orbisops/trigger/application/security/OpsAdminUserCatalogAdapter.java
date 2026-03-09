package cn.lgs.orbisops.trigger.application.security;

import cn.lgs.orbisops.application.security.AdminUserCatalogPort;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAdminUserRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AdminUserRecord;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;

import java.util.List;

/** Repository adapter for administrator/user account persistence. */
public final class OpsAdminUserCatalogAdapter implements AdminUserCatalogPort {

    private final IAdminUserRepository repository;

    public OpsAdminUserCatalogAdapter(IAdminUserRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("ADMIN_USER_REPOSITORY_REQUIRED");
        }
        this.repository = repository;
    }

    @Override
    public boolean insert(AdminUserAccount account) {
        return repository.insert(record(account)) > 0;
    }

    @Override
    public boolean updateById(AdminUserAccount account) {
        return repository.updateById(record(account)) > 0;
    }

    @Override
    public boolean updateByUserId(AdminUserAccount account) {
        return repository.updateByUserId(record(account)) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return repository.deleteById(id) > 0;
    }

    @Override
    public boolean deleteByUserId(String userId) {
        return repository.deleteByUserId(userId) > 0;
    }

    @Override
    public AdminUserAccount findById(Long id) {
        return account(repository.queryById(id));
    }

    @Override
    public AdminUserAccount findByUserId(String userId) {
        return account(repository.queryByUserId(userId));
    }

    @Override
    public AdminUserAccount findByUsername(String username) {
        return account(repository.queryByUsername(username));
    }

    @Override
    public List<AdminUserAccount> listEnabled() {
        return accounts(repository.queryEnabledUsers());
    }

    @Override
    public List<AdminUserAccount> listByStatus(Integer status) {
        return accounts(repository.queryByStatus(status));
    }

    @Override
    public List<AdminUserAccount> listAll() {
        return accounts(repository.queryAll());
    }

    private AdminUserRecord record(AdminUserAccount account) {
        if (account == null) {
            return null;
        }
        return AdminUserRecord.builder()
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

    private AdminUserAccount account(AdminUserRecord record) {
        if (record == null) {
            return null;
        }
        return new AdminUserAccount(
                record.getId(),
                record.getUserId(),
                record.getUsername(),
                record.getPassword(),
                AdminUserRole.parse(record.getUserRole(), AdminUserRole.ADMIN),
                record.getStatus(),
                record.getCreateTime(),
                record.getUpdateTime());
    }

    private List<AdminUserAccount> accounts(List<AdminUserRecord> records) {
        return records == null || records.isEmpty()
                ? List.of()
                : records.stream().map(this::account).toList();
    }
}
