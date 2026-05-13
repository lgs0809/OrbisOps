package cn.lgs.orbisops.application.memory;

@FunctionalInterface
public interface ContextMemoryStoreFailurePort {

    void onFailure(String operation, RuntimeException error);
}
