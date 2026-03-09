package cn.lgs.orbisops.domain.agenteval.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentEvalCase(
        String caseId,
        String name,
        String input,
        String expectedIntent,
        List<String> requiredNodes,
        List<String> forbiddenNodes,
        List<String> requiredRoles,
        List<String> forbiddenRoles,
        List<String> requiredEvidence,
        List<String> requiredSources,
        List<String> forbiddenSources,
        List<String> requiredTools,
        List<String> forbiddenTools,
        List<String> forbiddenEffects,
        List<String> requiredOutputFields,
        int maxLoopIterations,
        int maxToolCalls,
        long maxTokenCount,
        long maxLatencyMs,
        String expectedStatus,
        Boolean expectedChangePackageCreated,
        boolean requireFactInferenceUnknownSeparation,
        boolean requireEvidenceRefs,
        Map<String, Object> fixture) {

    public AgentEvalCase {
        caseId = text(caseId);
        name = text(name);
        input = required(input, "Agent Eval Case 缺少 input");
        expectedIntent = text(expectedIntent);
        requiredNodes = values(requiredNodes);
        forbiddenNodes = values(forbiddenNodes);
        requiredRoles = values(requiredRoles);
        forbiddenRoles = values(forbiddenRoles);
        requiredEvidence = values(requiredEvidence);
        requiredSources = values(requiredSources);
        forbiddenSources = values(forbiddenSources);
        requiredTools = values(requiredTools);
        forbiddenTools = values(forbiddenTools);
        forbiddenEffects = values(forbiddenEffects);
        requiredOutputFields = values(requiredOutputFields);
        maxLoopIterations = Math.max(0, maxLoopIterations);
        maxToolCalls = Math.max(0, maxToolCalls);
        maxTokenCount = Math.max(0L, maxTokenCount);
        maxLatencyMs = Math.max(0L, maxLatencyMs);
        expectedStatus = text(expectedStatus);
        fixture = fixture == null || fixture.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fixture));
        if (!hasDeterministicAssertion(
                expectedIntent,
                requiredNodes,
                forbiddenNodes,
                requiredRoles,
                forbiddenRoles,
                requiredEvidence,
                requiredSources,
                forbiddenSources,
                requiredTools,
                forbiddenTools,
                forbiddenEffects,
                requiredOutputFields,
                maxLoopIterations,
                maxToolCalls,
                maxTokenCount,
                maxLatencyMs,
                expectedStatus,
                expectedChangePackageCreated,
                requireFactInferenceUnknownSeparation,
                requireEvidenceRefs)) {
            throw new IllegalArgumentException("Agent Eval Case 至少需要一个确定性断言");
        }
    }

    public AgentEvalCase withCaseId(String id) {
        return new AgentEvalCase(id, name, input, expectedIntent, requiredNodes, forbiddenNodes,
                requiredRoles, forbiddenRoles, requiredEvidence, requiredSources, forbiddenSources,
                requiredTools, forbiddenTools, forbiddenEffects, requiredOutputFields,
                maxLoopIterations, maxToolCalls, maxTokenCount, maxLatencyMs, expectedStatus,
                expectedChangePackageCreated, requireFactInferenceUnknownSeparation,
                requireEvidenceRefs, fixture);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caseId", caseId);
        result.put("name", name);
        result.put("input", input);
        result.put("expectedIntent", expectedIntent);
        result.put("requiredNodes", requiredNodes);
        result.put("forbiddenNodes", forbiddenNodes);
        result.put("requiredRoles", requiredRoles);
        result.put("forbiddenRoles", forbiddenRoles);
        result.put("requiredEvidence", requiredEvidence);
        result.put("requiredSources", requiredSources);
        result.put("forbiddenSources", forbiddenSources);
        result.put("requiredTools", requiredTools);
        result.put("forbiddenTools", forbiddenTools);
        result.put("forbiddenEffects", forbiddenEffects);
        result.put("requiredOutputFields", requiredOutputFields);
        result.put("maxLoopIterations", maxLoopIterations);
        result.put("maxToolCalls", maxToolCalls);
        result.put("maxTokenCount", maxTokenCount);
        result.put("maxLatencyMs", maxLatencyMs);
        result.put("expectedStatus", expectedStatus);
        if (expectedChangePackageCreated != null) {
            result.put("expectedChangePackageCreated", expectedChangePackageCreated);
        }
        result.put("requireFactInferenceUnknownSeparation", requireFactInferenceUnknownSeparation);
        result.put("requireEvidenceRefs", requireEvidenceRefs);
        result.put("fixture", fixture);
        return result;
    }

    public static AgentEvalCase fromMap(Map<String, Object> source) {
        Map<String, Object> safe = source == null ? Map.of() : source;
        return new AgentEvalCase(
                text(safe.get("caseId")),
                text(safe.get("name")),
                text(safe.get("input")),
                text(safe.get("expectedIntent")),
                strings(safe.get("requiredNodes")),
                strings(safe.get("forbiddenNodes")),
                strings(safe.get("requiredRoles")),
                strings(safe.get("forbiddenRoles")),
                strings(safe.get("requiredEvidence")),
                strings(safe.get("requiredSources")),
                strings(safe.get("forbiddenSources")),
                strings(safe.get("requiredTools")),
                strings(safe.get("forbiddenTools")),
                strings(safe.get("forbiddenEffects")),
                strings(safe.get("requiredOutputFields")),
                integer(safe.get("maxLoopIterations")),
                integer(safe.get("maxToolCalls")),
                longValue(safe.get("maxTokenCount")),
                longValue(safe.get("maxLatencyMs")),
                text(safe.get("expectedStatus")),
                safe.containsKey("expectedChangePackageCreated")
                        ? Boolean.valueOf(Boolean.TRUE.equals(safe.get("expectedChangePackageCreated")))
                        : null,
                Boolean.TRUE.equals(safe.get("requireFactInferenceUnknownSeparation")),
                Boolean.TRUE.equals(safe.get("requireEvidenceRefs")),
                objectMap(safe.get("fixture")));
    }

    private static boolean hasDeterministicAssertion(
            String expectedIntent,
            List<String> requiredNodes,
            List<String> forbiddenNodes,
            List<String> requiredRoles,
            List<String> forbiddenRoles,
            List<String> requiredEvidence,
            List<String> requiredSources,
            List<String> forbiddenSources,
            List<String> requiredTools,
            List<String> forbiddenTools,
            List<String> forbiddenEffects,
            List<String> requiredOutputFields,
            int maxLoopIterations,
            int maxToolCalls,
            long maxTokenCount,
            long maxLatencyMs,
            String expectedStatus,
            Boolean expectedChangePackageCreated,
            boolean requireFactInferenceUnknownSeparation,
            boolean requireEvidenceRefs) {
        return !expectedIntent.isBlank()
                || !requiredNodes.isEmpty()
                || !forbiddenNodes.isEmpty()
                || !requiredEvidence.isEmpty()
                || !requiredRoles.isEmpty()
                || !forbiddenRoles.isEmpty()
                || !requiredSources.isEmpty()
                || !forbiddenSources.isEmpty()
                || !requiredTools.isEmpty()
                || !forbiddenTools.isEmpty()
                || !forbiddenEffects.isEmpty()
                || !requiredOutputFields.isEmpty()
                || maxLoopIterations > 0
                || maxToolCalls > 0
                || maxTokenCount > 0
                || maxLatencyMs > 0
                || !expectedStatus.isBlank()
                || requireFactInferenceUnknownSeparation
                || requireEvidenceRefs
                || expectedChangePackageCreated != null;
    }

    private static List<String> values(List<String> source) {
        if (source == null || source.isEmpty()) return List.of();
        return source.stream().map(AgentEvalCase::text).filter(value -> !value.isBlank()).toList();
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().map(AgentEvalCase::text).filter(item -> !item.isBlank()).toList();
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static int integer(Object value) {
        try {
            return value instanceof Number number ? number.intValue() : Integer.parseInt(text(value));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static long longValue(Object value) {
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(text(value));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static String required(String value, String message) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
