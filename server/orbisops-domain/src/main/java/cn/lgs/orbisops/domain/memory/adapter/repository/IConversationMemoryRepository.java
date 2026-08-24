package cn.lgs.orbisops.domain.memory.adapter.repository;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.ConversationMemoryWindow;
import cn.lgs.orbisops.domain.memory.model.MemoryProcessingJob;

import java.util.List;
import java.util.Optional;

/** Durable conversation order, summary CAS, and replay facts share the MySQL transaction boundary. */
public interface IConversationMemoryRepository {
    ColdMemoryMessageSnapshot capture(ColdMemoryMessageSnapshot message, int bufferSize);

    Optional<ConversationMemoryWindow> window(String sessionId, int limit, boolean newest);

    /** Check projection sources against retained, scoped facts; deleted conversations cannot reappear via a late vector write. */
    List<Long> existingSequences(String sessionId, String projectId, String userId, List<Long> sequences);

    java.util.Set<String> visibleSemanticProjections(String sessionId,String projectId,String userId,
            java.util.List<cn.lgs.orbisops.domain.memory.model.SemanticProjectionReference> projections);

    boolean commitSummary(ConversationMemoryWindow expected, long coveredSeq, String content,
                          List<ColdMemoryMessageSnapshot> protectedMessages, String source);

    List<Long> pending(String sessionId, int limit, long nowMillis);

    Optional<MemoryProcessingJob> claim(long id, String token, long nowMillis, long leaseMillis);

    /** The callback contains only idempotent local DB writes, never model/network calls. */
    boolean complete(MemoryProcessingJob job, String resultKind, long nowMillis, Runnable localWrites);

    boolean retry(MemoryProcessingJob job, String errorCode, long nowMillis);

    boolean validSemanticSource(MemoryProcessingJob job,long nowMillis);

    boolean block(MemoryProcessingJob job, String errorCode, long nowMillis);

    boolean defer(MemoryProcessingJob job, String resultKind, String errorCode, long nowMillis, Runnable localWrites);

    boolean commitPendingModelSummary(ConversationMemoryWindow expected, long coveredSeq, String content,
            List<ColdMemoryMessageSnapshot> protectedMessages, String source, String modelInputJson);

    boolean replacePendingModelSummary(cn.lgs.orbisops.domain.memory.model.MemorySummaryModelInput input, String content);
}
