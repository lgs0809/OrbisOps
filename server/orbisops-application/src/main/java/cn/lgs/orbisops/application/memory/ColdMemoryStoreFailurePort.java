package cn.lgs.orbisops.application.memory;

/** Optional outer-layer observer for fail-open Cold Memory persistence failures. */
@FunctionalInterface
public interface ColdMemoryStoreFailurePort {

    void onFailure(String operation, RuntimeException error);
}
