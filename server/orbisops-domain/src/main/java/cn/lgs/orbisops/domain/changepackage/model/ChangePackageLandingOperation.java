package cn.lgs.orbisops.domain.changepackage.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ChangePackageLandingOperation(String operationId,
                                            String operationHash,
                                            String adapterType,
                                            String toolsetId,
                                            String toolName,
                                            String resourceKey,
                                            String effectType,
                                            Map<String, Object> raw) {

    public ChangePackageLandingOperation {
        operationId = text(operationId);
        operationHash = text(operationHash);
        adapterType = text(adapterType);
        toolsetId = text(toolsetId);
        toolName = text(toolName);
        resourceKey = text(resourceKey);
        effectType = text(effectType);
        raw = raw == null || raw.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(raw));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
