package cn.lgs.orbisops.trigger.application.security;

import cn.lgs.orbisops.application.security.AdminUserAuthenticationPort;
import cn.lgs.orbisops.domain.agent.model.entity.AdminUserRecord;
import cn.lgs.orbisops.domain.security.AdminUserAccount;

/** Authentication protocol adapter backed by the existing AdminAuthService. */
public final class OpsAdminUserAuthenticationAdapter implements AdminUserAuthenticationPort {

    private final AdminAuthService authenticationService;

    public OpsAdminUserAuthenticationAdapter(AdminAuthService authenticationService) {
        if (authenticationService == null) {
            throw new IllegalArgumentException("ADMIN_AUTH_SERVICE_REQUIRED");
        }
        this.authenticationService = authenticationService;
    }

    @Override
    public boolean isEncoded(String credential) {
        return authenticationService.isBcrypt(credential);
    }

    @Override
    public String encodeForStorage(String credential) {
        return authenticationService.hashPasswordForStorage(credential);
    }

    @Override
    public boolean matches(String submittedCredential, String storedCredential) {
        return authenticationService.passwordMatches(submittedCredential, storedCredential);
    }

    @Override
    public String issueToken(AdminUserAccount account) {
        return authenticationService.issueToken(record(account));
    }

    @Override
    public boolean revokeAuthorization(String authorization) {
        return authenticationService.revokeAuthorization(authorization);
    }

    private AdminUserRecord record(AdminUserAccount account) {
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
}
