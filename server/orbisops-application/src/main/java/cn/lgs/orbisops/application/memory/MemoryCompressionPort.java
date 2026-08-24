package cn.lgs.orbisops.application.memory;

@FunctionalInterface
public interface MemoryCompressionPort {

    void compress(String sessionId, String userId, int bufferSize);

    default Runnable prepareCompression(cn.lgs.orbisops.domain.memory.model.MemoryProcessingJob job) {
        throw new IllegalStateException("MEMORY_FENCED_COMPRESSION_UNAVAILABLE");
    }

    /** Call the model outside a transaction, returning only fenced local projection writes. */
    default Runnable prepareModelRetry(cn.lgs.orbisops.domain.memory.model.MemoryProcessingJob job) {
        throw new IllegalStateException("MEMORY_MODEL_SUMMARY_RETRY_UNAVAILABLE");
    }
}
