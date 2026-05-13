package cn.lgs.orbisops.application.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed use-case command for verifying a governed PROJECT_FACT. */
public record GovernedMemoryVerifyCommand(
        String memoryId,
        List<Map<String, Object>> proofRefs,
        String actor) {

    public GovernedMemoryVerifyCommand {
        memoryId = value(memoryId);
        actor = value(actor);
        proofRefs = immutableMaps(proofRefs);
    }

    private static List<Map<String, Object>> immutableMaps(List<Map<String, Object>> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<Map<String, Object>> copy = new ArrayList<>(values.size());
        for (Map<String, Object> value : values) {
            copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(value == null ? Map.of() : value)));
        }
        return Collections.unmodifiableList(copy);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
