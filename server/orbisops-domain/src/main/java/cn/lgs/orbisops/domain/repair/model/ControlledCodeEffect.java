package cn.lgs.orbisops.domain.repair.model;

import java.util.Locale;

public enum ControlledCodeEffect {
    READ_ONLY,
    WRITE_REPAIR_WORKSPACE,
    TEST_OR_BUILD,
    VERIFY_STEP;

    public static ControlledCodeEffect require(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) return READ_ONLY;
        try {
            return valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的 expectedEffect：" + normalized, e);
        }
    }

    public boolean testOrVerify() {
        return this == TEST_OR_BUILD || this == VERIFY_STEP;
    }

    public boolean writerBound() {
        return this != READ_ONLY;
    }
}
