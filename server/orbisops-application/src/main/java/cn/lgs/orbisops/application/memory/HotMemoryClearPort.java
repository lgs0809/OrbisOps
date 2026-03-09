package cn.lgs.orbisops.application.memory;

@FunctionalInterface
public interface HotMemoryClearPort {

    void clear(String sessionId);
}
