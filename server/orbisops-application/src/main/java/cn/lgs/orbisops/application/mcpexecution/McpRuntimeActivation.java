package cn.lgs.orbisops.application.mcpexecution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed activation outcome; protocol-specific fields remain isolated in payload. */
public record McpRuntimeActivation(String status, Map<String, Object> payload) {

    public McpRuntimeActivation {
        status = status == null || status.isBlank() ? "SUCCEEDED" : status.trim().toUpperCase();
        payload = payload == null || payload.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
