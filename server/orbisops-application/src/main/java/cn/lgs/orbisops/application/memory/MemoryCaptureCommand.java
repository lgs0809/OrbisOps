package cn.lgs.orbisops.application.memory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed command for capturing one conversation message into runtime memory. */
public record MemoryCaptureCommand(
        String sessionId,
        String userId,
        String role,
        String content,
        Map<String, Object> metadata,
        int bufferSize,
        boolean extractionAsyncEnabled) {

    public MemoryCaptureCommand {
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        bufferSize = Math.max(2, bufferSize);
    }

    public boolean valid() {
        return hasText(sessionId) && hasText(content);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
