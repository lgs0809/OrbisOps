package cn.lgs.orbisops.application.security;

import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserStatus;

import java.time.Clock;
import java.time.LocalDateTime;

/** Application process manager for login validation, legacy credential migration and logout. */
public final class AdminUserAuthenticationUseCase {

    private final AdminUserCatalogPort catalogPort;
    private final AdminUserAuthenticationPort authenticationPort;
    private final Clock clock;

    public AdminUserAuthenticationUseCase(
            AdminUserCatalogPort catalogPort,
            AdminUserAuthenticationPort authenticationPort) {
        this(catalogPort, authenticationPort, Clock.systemDefaultZone());
    }

    public AdminUserAuthenticationUseCase(
            AdminUserCatalogPort catalogPort,
            AdminUserAuthenticationPort authenticationPort,
            Clock clock) {
        if (catalogPort == null) {
            throw new IllegalArgumentException("ADMIN_USER_CATALOG_PORT_REQUIRED");
        }
        if (authenticationPort == null) {
            throw new IllegalArgumentException("ADMIN_USER_AUTHENTICATION_PORT_REQUIRED");
        }
        if (clock == null) {
            throw new IllegalArgumentException("ADMIN_USER_CLOCK_REQUIRED");
        }
        this.catalogPort = catalogPort;
        this.authenticationPort = authenticationPort;
        this.clock = clock;
    }

    public AdminUserLoginResult login(AdminUserLoginCommand command) {
        AdminUserAccount account = authenticatedAccount(command);
        AdminUserAccount migrated = migrateLegacyCredentialIfNecessary(account, command.credential());
        return new AdminUserLoginResult(migrated, authenticationPort.issueToken(migrated));
    }

    public boolean validateLogin(AdminUserLoginCommand command) {
        AdminUserAccount account = authenticatedAccount(command);
        migrateLegacyCredentialIfNecessary(account, command.credential());
        return true;
    }

    public boolean logout(String authorization) {
        return authenticationPort.revokeAuthorization(authorization);
    }

    private AdminUserAccount authenticatedAccount(AdminUserLoginCommand command) {
        if (command == null || !hasText(command.username()) || !hasText(command.credential())) {
            throw new IllegalArgumentException("用户名或密码不能为空");
        }
        AdminUserAccount account = catalogPort.findByUsername(command.username());
        if (account == null
                || !authenticationPort.matches(command.credential(), account.credential())) {
            throw new AdminUserAuthenticationException("用户名或密码错误");
        }
        if (AdminUserStatus.isDisabled(account.status())) {
            throw new AdminUserAuthenticationException("用户已被禁用");
        }
        if (AdminUserStatus.isLocked(account.status())) {
            throw new AdminUserAuthenticationException("用户已被锁定");
        }
        return account;
    }

    private AdminUserAccount migrateLegacyCredentialIfNecessary(
            AdminUserAccount account,
            String submittedCredential) {
        if (authenticationPort.isEncoded(account.credential())) {
            return account;
        }
        AdminUserAccount migrated = account.withCredential(
                authenticationPort.encodeForStorage(submittedCredential),
                LocalDateTime.now(clock));
        catalogPort.updateById(migrated);
        return migrated;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
