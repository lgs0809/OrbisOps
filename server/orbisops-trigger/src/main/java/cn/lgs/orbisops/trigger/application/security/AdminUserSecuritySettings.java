package cn.lgs.orbisops.trigger.application.security;

/** Immutable password-ingress policy settings for admin/user accounts. */
public record AdminUserSecuritySettings(
        int passwordMinLength,
        boolean allowPrehashedPasswords) {

    public AdminUserSecuritySettings {
        passwordMinLength = Math.max(8, Math.min(passwordMinLength, 256));
    }

    public int minimumLength() {
        return passwordMinLength;
    }

    public boolean prehashedAllowed() {
        return allowPrehashedPasswords;
    }

    public static AdminUserSecuritySettings defaults() {
        return new AdminUserSecuritySettings(8, false);
    }
}
