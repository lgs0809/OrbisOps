package cn.lgs.orbisops.domain.memory.model;

/** Leased outbox identity. The original message remains the replay input. */
public record MemoryProcessingJob(long id, String sessionId, long messageSeq,
                                  String taskType, int bufferSize, int attempts,
                                  String leaseToken, long epoch, ColdMemoryMessageSnapshot message, String modelInputJson, String resultKind,String sourceHash) {
    public MemoryProcessingJob(long id,String sessionId,long messageSeq,String taskType,int bufferSize,int attempts,
            String leaseToken,long epoch,ColdMemoryMessageSnapshot message,String modelInputJson,String resultKind) {
        this(id,sessionId,messageSeq,taskType,bufferSize,attempts,leaseToken,epoch,message,modelInputJson,resultKind,"");
    }
    public MemoryProcessingJob(long id, String sessionId, long messageSeq, String taskType, int bufferSize,
            int attempts, String leaseToken, long epoch, ColdMemoryMessageSnapshot message) {
        this(id, sessionId, messageSeq, taskType, bufferSize, attempts, leaseToken, epoch, message, "", "", "");
    }
}
