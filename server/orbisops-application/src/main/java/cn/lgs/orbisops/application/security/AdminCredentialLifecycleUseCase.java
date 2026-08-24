package cn.lgs.orbisops.application.security;

import cn.lgs.orbisops.domain.security.AdminCredentialRules;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserStatus;

/** Self-service password change and administrator reset share one versioned lifecycle. */
public final class AdminCredentialLifecycleUseCase {
    private final AdminUserCatalogPort catalog;
    private final AdminCredentialLifecyclePort credentials;
    private final AdminUserAuthenticationPort authentication;
    private final AdminCredentialRules rules;

    public AdminCredentialLifecycleUseCase(AdminUserCatalogPort catalog, AdminCredentialLifecyclePort credentials,
            AdminUserAuthenticationPort authentication, AdminCredentialRules rules) {
        this.catalog = java.util.Objects.requireNonNull(catalog);
        this.credentials = java.util.Objects.requireNonNull(credentials);
        this.authentication = java.util.Objects.requireNonNull(authentication);
        this.rules = java.util.Objects.requireNonNull(rules);
    }

    public AdminUserLoginResult changePassword(String authenticatedUserId, String currentPassword, String newPassword) {
        AdminUserAccount account = requiredAccount(authenticatedUserId);
        if (!Integer.valueOf(AdminUserStatus.ENABLED).equals(account.status()))
            throw new IllegalArgumentException("账号不可用");
        if (!authentication.matches(currentPassword, account.credential()))
            throw new IllegalArgumentException("当前密码错误");
        if (newPassword == null || authentication.isEncoded(newPassword))
            throw new IllegalArgumentException("请填写新的登录密码");
        rules.validate(newPassword, false);
        if (authentication.matches(newPassword, account.credential())
                || authentication.matches(newPassword, account.initialCredential()))
            throw new IllegalArgumentException("新密码不能与当前密码或初始密码相同");
        if (!credentials.changeCredential(account.userId(), account.credentialVersion(), authentication.encodeForStorage(newPassword)))
            throw new IllegalArgumentException("账号已发生变化，请重新登录后再试");
        AdminUserAccount updated = requiredAccount(account.userId());
        return new AdminUserLoginResult(updated, authentication.issueToken(updated));
    }

    public boolean resetPassword(String userId) {
        AdminUserAccount account = requiredAccount(userId);
        if (account.initialCredential() == null || account.initialCredential().isBlank())
            throw new IllegalArgumentException("旧账号未保存初始密码，请先由管理员设置初始密码");
        if (!credentials.resetToInitialCredential(account.userId(), account.credentialVersion()))
            throw new IllegalArgumentException("账号已发生变化，请刷新后再试");
        return true;
    }

    private AdminUserAccount requiredAccount(String userId) {
        AdminUserAccount account = catalog.findByUserId(userId);
        if (account == null) throw new IllegalArgumentException("账号不存在");
        return account;
    }
}
