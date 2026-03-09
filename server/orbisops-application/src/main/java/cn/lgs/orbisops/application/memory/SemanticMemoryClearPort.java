package cn.lgs.orbisops.application.memory;

@FunctionalInterface
public interface SemanticMemoryClearPort {

    void clear(String sessionId);
}
