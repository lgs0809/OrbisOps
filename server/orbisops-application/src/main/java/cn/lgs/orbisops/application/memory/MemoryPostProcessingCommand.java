package cn.lgs.orbisops.application.memory;

/** Typed request for semantic indexing, extraction and compression after a captured message. */
public record MemoryPostProcessingCommand(
        MemoryMessageView message,
        int bufferSize,
        boolean extractionAsyncEnabled) {

    public MemoryPostProcessingCommand {
        bufferSize = Math.max(2, bufferSize);
    }

    public boolean valid() {
        return message != null
                && message.sessionId() != null
                && !message.sessionId().trim().isBlank()
                && message.content() != null
                && !message.content().trim().isBlank();
    }
}
