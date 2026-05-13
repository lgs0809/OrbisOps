package cn.lgs.orbisops.application.memory;

@FunctionalInterface
public interface MemorySessionClearFailurePort {

    void onFailure(String operation, RuntimeException error);
}
