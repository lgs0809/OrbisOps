package cn.lgs.orbisops.trigger.application.ops;

/** Typed synchronous Chat runtime timeout settings. Zero disables the timeout guard. */
public record OpsChatRuntimeSettings(long syncTimeoutSeconds) {

    public OpsChatRuntimeSettings {
        syncTimeoutSeconds = Math.max(0L, Math.min(syncTimeoutSeconds, 3600L));
    }

    public static OpsChatRuntimeSettings defaults() {
        return new OpsChatRuntimeSettings(90L);
    }
}
