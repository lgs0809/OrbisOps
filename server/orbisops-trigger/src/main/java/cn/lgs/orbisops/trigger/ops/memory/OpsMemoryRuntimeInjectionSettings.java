package cn.lgs.orbisops.trigger.ops.memory;

/** Typed bound for governed-memory injection into one runtime request. */
public record OpsMemoryRuntimeInjectionSettings(int maxInjectionCount) {

    public OpsMemoryRuntimeInjectionSettings {
        maxInjectionCount = maxInjectionCount < 0 || maxInjectionCount > 100
                ? 8
                : maxInjectionCount;
    }

    public static OpsMemoryRuntimeInjectionSettings defaults() {
        return new OpsMemoryRuntimeInjectionSettings(8);
    }
}
