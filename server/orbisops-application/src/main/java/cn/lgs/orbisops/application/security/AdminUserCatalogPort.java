package cn.lgs.orbisops.application.security;

import cn.lgs.orbisops.domain.security.AdminUserAccount;

import java.util.List;

/** Persistence boundary for administrator/user accounts. */
public interface AdminUserCatalogPort {

    boolean insert(AdminUserAccount account);

    boolean updateById(AdminUserAccount account);

    boolean updateByUserId(AdminUserAccount account);

    boolean deleteById(Long id);

    boolean deleteByUserId(String userId);

    AdminUserAccount findById(Long id);

    AdminUserAccount findByUserId(String userId);

    AdminUserAccount findByUsername(String username);

    List<AdminUserAccount> listEnabled();

    List<AdminUserAccount> listByStatus(Integer status);

    List<AdminUserAccount> listAll();
}
