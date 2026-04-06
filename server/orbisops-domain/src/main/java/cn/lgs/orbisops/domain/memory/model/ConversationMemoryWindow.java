package cn.lgs.orbisops.domain.memory.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One consistent database snapshot; summary coverage never includes the live tail. */
public record ConversationMemoryWindow(
        String sessionId, String projectId, String userId, long lastSeq,
        long coveredSeq, long summaryRevision, String summaryContent,
        List<ColdMemoryMessageSnapshot> protectedMessages,
        List<ColdMemoryMessageSnapshot> messages) {

    public ConversationMemoryWindow {
        summaryContent = summaryContent == null ? "" : summaryContent;
        protectedMessages = protectedMessages == null ? List.of() : List.copyOf(protectedMessages);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public List<ColdMemoryMessageSnapshot> context() {
        List<ColdMemoryMessageSnapshot> result = new ArrayList<>();
        if (!summaryContent.isBlank()) {
            // This is historical data, never a new system instruction.
            result.add(new ColdMemoryMessageSnapshot(sessionId, userId, "assistant", summaryContent, "",
                    Map.of("memory_type", "summary", "coveredSeq", coveredSeq,
                            "summaryRevision", summaryRevision, "projectId", projectId)));
        }
        result.addAll(messages);
        return List.copyOf(result);
    }

    public static long sequence(ColdMemoryMessageSnapshot message) {
        Object value = message.metadata().get("messageSeq");
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
