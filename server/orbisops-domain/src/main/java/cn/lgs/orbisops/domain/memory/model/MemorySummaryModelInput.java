package cn.lgs.orbisops.domain.memory.model;

import java.util.List;

/** Original source for a new failed model summary, never reconstructed from a later rule summary. */
public record MemorySummaryModelInput(String sessionId, String projectId, String userId,
        long coveredSeq, long summaryRevision, int maxInputChars, List<ColdMemoryMessageSnapshot> messages) {
    public MemorySummaryModelInput {
        if (sessionId == null || sessionId.isBlank() || projectId == null || userId == null
                || userId.isBlank() || coveredSeq < 1 || summaryRevision < 1 || maxInputChars<1 || maxInputChars>200_000
                || messages == null || messages.isEmpty())
            throw new MemoryModelInputInvalidException("MEMORY_MODEL_SUMMARY_INPUT_INVALID");
        messages = List.copyOf(messages);
        for(var message:messages) {
            if(!sessionId.equals(message.sessionId()) || !projectId.equals(message.metadata().get("projectId")))
                throw new MemoryModelInputInvalidException("MEMORY_MODEL_SUMMARY_SCOPE_INVALID");
        }
    }
    public void requireScope(String session,String project,String user) {
        if(!sessionId.equals(session) || !projectId.equals(project) || !userId.equals(user))
            throw new MemoryModelInputInvalidException("MEMORY_MODEL_SUMMARY_SCOPE_INVALID");
    }
    @SuppressWarnings("unchecked")
    public static MemorySummaryModelInput parse(String raw) {
        try {
            var input=cn.lgs.orbisops.domain.shared.json.CanonicalJson.parseObject(raw);
            var sources=((List<java.util.Map<String,Object>>)input.get("messages")).stream().map(m ->
                    new ColdMemoryMessageSnapshot((String)m.get("sessionId"),(String)m.get("userId"),(String)m.get("role"),
                            (String)m.get("content"),(String)m.get("createdAt"),(java.util.Map<String,Object>)m.get("metadata"))).toList();
            return new MemorySummaryModelInput((String)input.get("sessionId"),(String)input.get("projectId"),(String)input.get("userId"),
                    ((Number)input.get("coveredSeq")).longValue(),((Number)input.get("summaryRevision")).longValue(),
                    ((Number)input.get("maxInputChars")).intValue(),sources);
        } catch(RuntimeException invalid) {throw new MemoryModelInputInvalidException("MEMORY_MODEL_SUMMARY_INPUT_INVALID",invalid);}
    }
}
