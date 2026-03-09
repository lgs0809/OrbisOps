package cn.lgs.orbisops.application.memory;

/** Secondary port for clearing one session's semantic-memory persistence. */
@FunctionalInterface
public interface SemanticMemoryClearPersistencePort {

    boolean clearSession(String sessionId);
}
