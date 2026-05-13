package cn.lgs.orbisops.application.memory;

@FunctionalInterface
public interface MemoryCompressionPort {

    void compress(String sessionId, String userId, int bufferSize);
}
