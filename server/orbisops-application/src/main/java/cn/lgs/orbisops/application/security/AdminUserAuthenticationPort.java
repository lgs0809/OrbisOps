package cn.lgs.orbisops.application.security;

import cn.lgs.orbisops.domain.security.AdminUserAccount;

/** Authentication protocol boundary for account credentials and access tokens. */
public interface AdminUserAuthenticationPort {

    boolean isEncoded(String credential);

    String encodeForStorage(String credential);

    boolean matches(String submittedCredential, String storedCredential);

    String issueToken(AdminUserAccount account);

    boolean revokeAuthorization(String authorization);
}
