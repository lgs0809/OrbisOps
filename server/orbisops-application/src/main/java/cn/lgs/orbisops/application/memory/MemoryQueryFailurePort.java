package cn.lgs.orbisops.application.memory;

@FunctionalInterface
public interface MemoryQueryFailurePort {

    void onFailure(String operation, RuntimeException error);
}
