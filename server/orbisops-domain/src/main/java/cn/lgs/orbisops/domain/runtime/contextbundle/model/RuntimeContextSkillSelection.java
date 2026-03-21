package cn.lgs.orbisops.domain.runtime.contextbundle.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RuntimeContextSkillSelection(
        List<Map<String, Object>> catalogRefs,
        List<Map<String, Object>> selectedRefs,
        List<Map<String, Object>> suppressedRefs,
        int activeCount,
        int catalogCount) {

    public RuntimeContextSkillSelection {
        catalogRefs = immutable(catalogRefs);
        selectedRefs = immutable(selectedRefs);
        suppressedRefs = immutable(suppressedRefs);
        activeCount = Math.max(0, activeCount);
        catalogCount = Math.max(0, catalogCount);
    }

    private static List<Map<String, Object>> immutable(List<Map<String, Object>> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : source) {
            result.add(Collections.unmodifiableMap(new LinkedHashMap<>(item == null ? Map.of() : item)));
        }
        return Collections.unmodifiableList(result);
    }
}
