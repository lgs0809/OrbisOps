package cn.lgs.orbisops.application.security;

/** Business rejection raised by the account authentication flow. */
public class AdminUserAuthenticationException extends RuntimeException {

    public AdminUserAuthenticationException(String message) {
        super(message);
    }
}
