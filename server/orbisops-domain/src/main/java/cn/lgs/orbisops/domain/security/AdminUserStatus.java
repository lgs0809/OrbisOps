package cn.lgs.orbisops.domain.security;

import java.util.Set;

/** Supported lifecycle states for administrator and regular user accounts. */
public final class AdminUserStatus {

    public static final int DISABLED = 0;
    public static final int ENABLED = 1;
    public static final int LOCKED = 2;

    private static final Set<Integer> VALID = Set.of(DISABLED, ENABLED, LOCKED);

    private AdminUserStatus() {
    }

    public static Integer validate(Integer status) {
        if (status != null && !VALID.contains(status)) {
            throw new IllegalArgumentException("用户状态只允许 0、1、2");
        }
        return status;
    }

    public static boolean isDisabled(Integer status) {
        return Integer.valueOf(DISABLED).equals(status);
    }

    public static boolean isLocked(Integer status) {
        return Integer.valueOf(LOCKED).equals(status);
    }
}
