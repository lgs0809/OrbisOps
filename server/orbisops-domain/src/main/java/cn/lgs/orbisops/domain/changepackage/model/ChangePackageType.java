package cn.lgs.orbisops.domain.changepackage.model;

import java.util.Locale;
import java.util.Set;

public enum ChangePackageType {
    NO_ACTION_REQUIRED,
    GIT_BRANCH_REPAIR,
    MCP_OPERATION_PACKAGE,
    CONFIG_PACKAGE,
    RELEASE_PACKAGE,
    MANUAL_REQUIRED,
    NEEDS_HUMAN_DESIGN;

    private static final Set<ChangePackageType> HUMAN_ONLY = Set.of(
            NO_ACTION_REQUIRED,
            MANUAL_REQUIRED,
            NEEDS_HUMAN_DESIGN);

    private static final Set<ChangePackageType> EXECUTABLE = Set.of(
            GIT_BRANCH_REPAIR,
            MCP_OPERATION_PACKAGE,
            CONFIG_PACKAGE,
            RELEASE_PACKAGE);

    public static ChangePackageType require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_TYPE_REQUIRED");
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_TYPE_UNKNOWN:" + normalized);
        }
    }

    public boolean humanOnly() {
        return HUMAN_ONLY.contains(this);
    }

    public boolean executable() {
        return EXECUTABLE.contains(this);
    }
}
