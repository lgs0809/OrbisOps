package cn.lgs.orbisops.trigger.ops.change;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Projects the frozen runtime context bundle into PREPARE package and evidence views. */
final class OpsPreparationContextProjectionFactory {

    Map<String, Object> packageContext(Map<String, Object> contextBundle) {
        Map<String, Object> bundle = safe(contextBundle);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("contextBundleId", text(bundle.get("contextBundleId"), ""));
        context.put("contextBundleHash", text(bundle.get("contextBundleHash"), ""));
        context.put("memoryContextRefs", bundle.getOrDefault("memoryContextRefs", List.of()));
        context.put("memoryContextHash", text(bundle.get("memoryContextHash"), ""));
        context.put("compressedMemorySummary", text(bundle.get("compressedMemorySummary"), ""));
        context.put("memoryInjectionVersion", text(bundle.get("memoryInjectionVersion"), ""));
        context.put("usedSkillVersionRefs", bundle.getOrDefault("usedSkillVersionRefs", List.of()));
        context.put("usedSkillRefsHash", text(bundle.get("usedSkillRefsHash"), ""));
        context.put("toolsetRefs", bundle.getOrDefault("toolsetRefs", List.of()));
        context.put("policyRefs", bundle.getOrDefault("policyRefs", List.of()));
        context.put("policyHash", text(bundle.get("policyHash"), ""));
        context.put("toolsetBoundaryHash", text(bundle.get("toolsetBoundaryHash"), ""));
        context.put("runtimeBoundaryHash", text(bundle.get("runtimeBoundaryHash"), ""));
        context.put("contextApprovalBoundaryHash", text(bundle.get("approvalBoundaryHash"), ""));
        return context;
    }

    Map<String, Object> evidenceContext(Map<String, Object> contextBundle) {
        Map<String, Object> bundle = safe(contextBundle);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("contextBundleId", text(bundle.get("contextBundleId"), ""));
        context.put("contextBundleHash", text(bundle.get("contextBundleHash"), ""));
        context.put("memoryContextRefs", bundle.getOrDefault("memoryContextRefs", List.of()));
        context.put("memoryContextHash", text(bundle.get("memoryContextHash"), ""));
        context.put("compressedMemorySummary", text(bundle.get("compressedMemorySummary"), ""));
        context.put("usedSkillVersionRefs", bundle.getOrDefault("usedSkillVersionRefs", List.of()));
        context.put("usedSkillRefsHash", text(bundle.get("usedSkillRefsHash"), ""));
        context.put("toolsetBoundaryHash", text(bundle.get("toolsetBoundaryHash"), ""));
        context.put("runtimeBoundaryHash", text(bundle.get("runtimeBoundaryHash"), ""));
        return context;
    }

    List<Object> skillRefValues(Map<String, Object> contextBundle, String key) {
        Object refs = safe(contextBundle).get("usedSkillVersionRefs");
        if (!(refs instanceof Iterable<?> iterable)) return List.of();
        List<Object> values = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map && map.containsKey(key)) {
                values.add(map.get(key));
            }
        }
        return List.copyOf(values);
    }

    private Map<String, Object> safe(Map<String, Object> source) {
        return source == null ? Map.of() : source;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
