package cn.lgs.orbisops.application.security;

/** Command used to create or update one administrator/user account. */
public record AdminUserCommand(
        Long id,
        String userId,
        String username,
        String credential,
        String userRole,
        Integer status) {
}
