package cn.lgs.orbisops.application.runtime.contextbundle;

import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleLayerInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RuntimeContextBundleCreateCommand(
        RuntimeContextBundleLayerInput layerInput,
        List<Map<String, Object>> memoryRefs,
        List<String> requestedSkillIds,
        int selectedSkillLimit) {

    public RuntimeContextBundleCreateCommand {
        if (layerInput == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_INPUT_REQUIRED");
        memoryRefs = immutableMapList(memoryRefs);
        requestedSkillIds = requestedSkillIds == null ? List.of() : requestedSkillIds.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        selectedSkillLimit = Math.max(1, selectedSkillLimit);
    }

    private static List<Map<String, Object>> immutableMapList(List<Map<String, Object>> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : source) {
            result.add(Collections.unmodifiableMap(new LinkedHashMap<>(item == null ? Map.of() : item)));
        }
        return Collections.unmodifiableList(result);
    }
}
