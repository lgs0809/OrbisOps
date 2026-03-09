package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;

import java.util.List;

/** Typed input for context compression orchestration. */
public record MemoryCompressionCommand(
        String sessionId,
        String userId,
        List<ColdMemoryMessageSnapshot> messages,
        int thresholdMessages,
        int keepRecent,
        boolean modelEnabled,
        int modelMaxInputChars,
        int hotBufferMessages) {

    public MemoryCompressionCommand {
        sessionId = value(sessionId);
        userId = value(userId);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
