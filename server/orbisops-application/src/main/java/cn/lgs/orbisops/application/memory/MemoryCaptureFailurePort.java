package cn.lgs.orbisops.application.memory;

@FunctionalInterface
public interface MemoryCaptureFailurePort {

    void onFailure(String operation, RuntimeException error);
}
