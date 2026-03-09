package cn.lgs.orbisops.trigger.http.admin.security;

/** Immutable local rate-limit policy settings. */
public record OpsRateLimitSettings(
        boolean enabled,
        int perUserPerMinute,
        int perAgentPathPerMinute) {

    public OpsRateLimitSettings {
        perUserPerMinute = bounded(perUserPerMinute);
        perAgentPathPerMinute = bounded(perAgentPathPerMinute);
    }

    public static OpsRateLimitSettings defaults() {
        return new OpsRateLimitSettings(true, 120, 240);
    }

    private static int bounded(int value) {
        return Math.max(1, Math.min(value, 10_000));
    }
}
