package cn.lgs.orbisops.application.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable authoring facts plus the immutable open authored payload. */
public record SkillEvolutionAuthoredCandidate(
        String patchType,
        List<Map<String, Object>> changes,
        List<Map<String, Object>> artifacts,
        List<Map<String, Object>> evalCases,
        String reason,
        String source,
        Map<String, Object> payload
) {

    public SkillEvolutionAuthoredCandidate {
        patchType = text(patchType, "NO_CHANGE");
        changes = maps(changes);
        artifacts = maps(artifacts);
        evalCases = maps(evalCases);
        reason = text(reason, "");
        source = text(source, "");
        payload = immutable(payload);
    }

    public static SkillEvolutionAuthoredCandidate from(Map<String, Object> values) {
        Map<String, Object> safe = immutable(values);
        return new SkillEvolutionAuthoredCandidate(
                text(safe.get("patchType"), "NO_CHANGE"),
                maps(safe.get("changes")),
                maps(safe.get("artifacts")),
                maps(safe.get("evalCases")),
                text(safe.get("reason"), ""),
                text(safe.get("authoringSource"), ""),
                safe);
    }

    public boolean reusableChange() {
        return !"NO_CHANGE".equalsIgnoreCase(patchType) && !changes.isEmpty();
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, field) -> copy.put(String.valueOf(key), field));
            result.add(Collections.unmodifiableMap(copy));
        }
        return List.copyOf(result);
    }

    private static String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
