package cn.lgs.orbisops.application.memory;

/** Best-effort observer for semantic-memory write degradation. */
@FunctionalInterface
public interface SemanticMemoryWriteFailurePort {

    void onWriteFailure(String operation, RuntimeException error);
}
