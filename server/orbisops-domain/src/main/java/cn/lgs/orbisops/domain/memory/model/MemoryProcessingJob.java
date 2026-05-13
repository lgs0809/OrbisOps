package cn.lgs.orbisops.domain.memory.model;

/** Leased outbox identity. The original message remains the replay input. */
public record MemoryProcessingJob(long id, String sessionId, long messageSeq,
                                  String taskType, int bufferSize, int attempts,
                                  String leaseToken, long epoch, ColdMemoryMessageSnapshot message) {
}
