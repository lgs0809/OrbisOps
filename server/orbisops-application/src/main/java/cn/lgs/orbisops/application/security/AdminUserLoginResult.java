package cn.lgs.orbisops.application.security;

import cn.lgs.orbisops.domain.security.AdminUserAccount;

/** Authenticated account and optional issued access token. */
public record AdminUserLoginResult(
        AdminUserAccount account,
        String token) {
}
