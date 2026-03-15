package cn.lgs.orbisops.domain.security;

/** Supported account authorization roles. */
public enum AdminUserRole {
    ADMIN("admin"),
    USER("user");

    private final String value;

    AdminUserRole(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static AdminUserRole parse(String input, AdminUserRole fallback) {
        if (input == null || input.trim().isBlank()) {
            return fallback;
        }
        String normalized = input.trim().toLowerCase();
        for (AdminUserRole role : values()) {
            if (role.value.equals(normalized)) {
                return role;
            }
        }
        throw new IllegalArgumentException("用户角色只允许 admin 或 user");
    }
}
