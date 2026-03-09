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
        LocalDateTime updateTime) {

    public AdminUserAccount withCredential(String encodedCredential, LocalDateTime resolvedUpdateTime) {
        return new AdminUserAccount(
                id,
                userId,
                username,
                encodedCredential,
                role,
                status,
                createTime,
                resolvedUpdateTime);
    }
}
