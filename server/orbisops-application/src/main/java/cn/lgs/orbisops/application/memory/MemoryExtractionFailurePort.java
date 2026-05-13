package cn.lgs.orbisops.application.memory;

/** Best-effort observer for optional memory extraction failures. */
@FunctionalInterface
public interface MemoryExtractionFailurePort {

    void onFailure(String operation, RuntimeException error);
}
