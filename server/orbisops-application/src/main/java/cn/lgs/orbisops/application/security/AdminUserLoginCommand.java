package cn.lgs.orbisops.application.security;

/** Credentials submitted to the account authentication flow. */
public record AdminUserLoginCommand(
        String username,
        String credential) {
}
