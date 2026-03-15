package cn.lgs.orbisops.trigger.application.security;

import cn.lgs.orbisops.application.security.AdminUserAuthenticationPort;
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
        return authenticationService.issueToken(account);
    }

    @Override
    public boolean revokeAuthorization(String authorization) {
        return authenticationService.revokeAuthorization(authorization);
    }
}
