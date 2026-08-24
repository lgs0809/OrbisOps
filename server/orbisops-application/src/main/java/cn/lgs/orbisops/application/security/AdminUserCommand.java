package cn.lgs.orbisops.application.security;

/** Command used to create or update one administrator/user account. */
public record AdminUserCommand(
        Long id,
        String userId,
        String username,
        String credential,
        String userRole,
        Integer status,
        String phone) {

    public AdminUserCommand(Long id, String userId, String username, String credential,
                            String userRole, Integer status) {
        this(id, userId, username, credential, userRole, status, null);
    }

    @Override
    public String toString() {
        return "AdminUserCommand[id=" + id + ", userId=" + userId + ", role=" + userRole
                + ", status=" + status + "]";
    }
}
