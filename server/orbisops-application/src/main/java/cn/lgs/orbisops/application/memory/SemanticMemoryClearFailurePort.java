package cn.lgs.orbisops.application.memory;

/** Best-effort observer for semantic-memory clear failures. */
@FunctionalInterface
public interface SemanticMemoryClearFailurePort {

    void onClearFailure(RuntimeException error);
}
