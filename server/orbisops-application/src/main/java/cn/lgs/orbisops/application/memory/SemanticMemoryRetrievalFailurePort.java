package cn.lgs.orbisops.application.memory;

/** Best-effort observer for semantic retrieval degradation. */
@FunctionalInterface
public interface SemanticMemoryRetrievalFailurePort {

    void onFailure(String operation, RuntimeException error);
}
