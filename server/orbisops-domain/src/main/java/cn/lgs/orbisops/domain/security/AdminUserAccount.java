package cn.lgs.orbisops.domain.security;

import java.time.LocalDateTime;

/** Immutable account aggregate used by administrator/user catalog and authentication flows. */
public record AdminUserAccount(
        Long id,
        String userId,
        String username,
        String credential,
        AdminUserRole role,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        String phone,
        String initialCredential,
        boolean passwordChangeRequired,
        long credentialVersion) {

    public AdminUserAccount(Long id, String userId, String username, String credential,
                            AdminUserRole role, Integer status, LocalDateTime createTime,
                            LocalDateTime updateTime, String phone) {
        this(id, userId, username, credential, role, status, createTime, updateTime, phone, null, false, 0);
    }

    public AdminUserAccount(Long id, String userId, String username, String credential,
                            AdminUserRole role, Integer status, LocalDateTime createTime,
                            LocalDateTime updateTime) {
        this(id, userId, username, credential, role, status, createTime, updateTime, null);
    }

    public AdminUserAccount withCredential(String encodedCredential, LocalDateTime resolvedUpdateTime) {
        return new AdminUserAccount(
                id,
                userId,
                username,
                encodedCredential,
                role,
                status,
                createTime,
                resolvedUpdateTime,
                phone,
                initialCredential,
                passwordChangeRequired,
                credentialVersion);
    }

    @Override
    public String toString() {
        return "AdminUserAccount[id=" + id + ", userId=" + userId + ", role=" + role
                + ", status=" + status + "]";
    }
}
