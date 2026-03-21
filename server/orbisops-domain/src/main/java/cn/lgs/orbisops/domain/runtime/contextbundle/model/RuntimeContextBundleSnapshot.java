package cn.lgs.orbisops.domain.runtime.contextbundle.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RuntimeContextBundleSnapshot(
        Long persistenceId,
        String bundleId,
        String bundleHash,
        String sessionId,
        String runId,
        String projectId,
        String agentId,
        String actor,
        String memoryContextHash,
        List<Map<String, Object>> memoryRefs,
        List<Map<String, Object>> usedSkillVersionRefs,
        String usedSkillRefsHash,
        String toolsetBoundaryHash,
        String runtimeBoundaryHash,
        Map<String, Object> payload,
        Instant createdAt) {

    public RuntimeContextBundleSnapshot {
        bundleId = required(bundleId, "RUNTIME_CONTEXT_BUNDLE_ID_REQUIRED");
        bundleHash = required(bundleHash, "RUNTIME_CONTEXT_BUNDLE_HASH_REQUIRED");
        sessionId = text(sessionId);
        runId = text(runId);
        projectId = text(projectId);
        agentId = text(agentId);
        actor = text(actor);
        memoryContextHash = text(memoryContextHash);
        memoryRefs = immutableMapList(memoryRefs);
        usedSkillVersionRefs = immutableMapList(usedSkillVersionRefs);
        usedSkillRefsHash = text(usedSkillRefsHash);
        toolsetBoundaryHash = text(toolsetBoundaryHash);
        runtimeBoundaryHash = text(runtimeBoundaryHash);
        payload = immutableMap(payload);
        if (createdAt == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_TIME_REQUIRED");
    }

    public Map<String, Object> compatiblePayload() {
        Map<String, Object> result = new LinkedHashMap<>(payload);
        result.put("contextBundleId", bundleId);
        result.put("contextBundleHash", bundleHash);
        result.putIfAbsent("memoryContextRefs", memoryRefs);
        result.putIfAbsent("memoryContextHash", memoryContextHash);
        result.putIfAbsent("usedSkillVersionRefs", usedSkillVersionRefs);
        result.putIfAbsent("usedSkillRefsHash", usedSkillRefsHash);
        result.putIfAbsent("toolsetBoundaryHash", toolsetBoundaryHash);
        result.putIfAbsent("runtimeBoundaryHash", runtimeBoundaryHash);
        return result;
    }

    private static List<Map<String, Object>> immutableMapList(List<Map<String, Object>> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : source) result.add(immutableMap(item));
        return Collections.unmodifiableList(result);
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String required(String value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
