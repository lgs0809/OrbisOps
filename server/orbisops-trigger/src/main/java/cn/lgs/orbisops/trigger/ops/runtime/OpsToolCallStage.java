package cn.lgs.orbisops.trigger.ops.runtime;

public enum OpsToolCallStage {
    UNKNOWN,
    SYSTEM_DISCOVERY,
    INVESTIGATE,
    PREPARE,
    LANDING;

    public static OpsToolCallStage from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return OpsToolCallStage.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
