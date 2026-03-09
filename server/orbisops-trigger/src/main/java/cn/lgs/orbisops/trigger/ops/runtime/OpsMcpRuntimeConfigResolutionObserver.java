package cn.lgs.orbisops.trigger.ops.runtime;

@FunctionalInterface
public interface OpsMcpRuntimeConfigResolutionObserver {

    void observe(OpsMcpRuntimeConfigResolution resolution);

    static OpsMcpRuntimeConfigResolutionObserver noop() {
        return resolution -> { };
    }
}
