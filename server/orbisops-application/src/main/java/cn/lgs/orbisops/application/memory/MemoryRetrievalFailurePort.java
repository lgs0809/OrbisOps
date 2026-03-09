package cn.lgs.orbisops.application.memory;

/** Optional outer-layer observer for isolated retrieval slice failures. */
@FunctionalInterface
public interface MemoryRetrievalFailurePort {

    void onFailure(String slice, RuntimeException error);
}
