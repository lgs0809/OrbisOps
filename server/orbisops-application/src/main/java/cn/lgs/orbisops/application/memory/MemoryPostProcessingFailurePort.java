package cn.lgs.orbisops.application.memory;

@FunctionalInterface
public interface MemoryPostProcessingFailurePort {

    void onFailure(String operation, RuntimeException error);
}
