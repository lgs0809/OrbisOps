package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds candidate, approval, preferred-plan and adjustment views for PREPARE. */
final class OpsPreparationPlanEnvelopeFactory {

    private static final List<String> DEFAULT_EFFECT_TYPES =
            List.of("READ_EXTERNAL_STATE", "VALIDATE_ONLY", "DRY_RUN");
    private static final List<String> DEFAULT_EFFECT_SCOPES = List.of(
            "PLATFORM_INTERNAL",
            "VALIDATION_ENVIRONMENT",
            "SANDBOX",
            "TEMPORARY_RESOURCE",
            "MUTATE_EPHEMERAL",
            "TEST_ENVIRONMENT");
    private static final List<String> REPLAN_TRIGGERS = List.of(
            "NEED_NEW_MCP_TOOL",
            "NEED_NEW_RESOURCE_SCOPE",
            "NEED_NEW_EFFECT_TYPE",
            "RISK_LEVEL_INCREASED",
            "ARGUMENT_OUT_OF_BOUNDARY",
            "POLICY_STALE_OR_UNKNOWN",
            "EXECUTOR_NOT_CONFIGURED");

    Envelope create(Input input) {
        if (input == null) throw new IllegalArgumentException("PREPARATION_PLAN_INPUT_REQUIRED");
        List<Map<String, Object>> candidates = candidatePlans(input);
        List<String> allowedTools = allowedToolsFromSteps(input.mcpSteps());
        Map<String, Object> approvalBoundary = approvalBoundary(input, allowedTools);
        Map<String, Object> preferredPlan = preferredPlan(input, candidates);
        Map<String, Object> adjustmentPolicy = adjustmentPolicy(input);
        List<Map<String, Object>> allowedAdjustments = allowedLandingAdjustments(input.request());
        return new Envelope(
                candidates,
                approvalBoundary,
                preferredPlan,
                adjustmentPolicy,
                allowedAdjustments,
                allowedTools,
                REPLAN_TRIGGERS);
    }

    private List<Map<String, Object>> candidatePlans(Input input) {
        List<Object> provided = listValue(firstNonNull(
                input.request().get("candidatePlans"),
                input.request().get("candidatePackages")));
        if (!provided.isEmpty()) {
            return provided.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> copy((Map<?, ?>) item))
                    .toList();
        }
        List<Map<String, Object>> plans = new ArrayList<>();
        plans.add(Map.of(
                "candidateId", "candidate-1",
                "objective", input.objective(),
                "status", "DRAFTED",
                "steps", input.mcpSteps(),
                "limitations", input.limitations(),
                "preflightStatus", "PARTIAL",
                "dryRunStatus", "NOT_SUPPORTED"));
        if (!"ACCEPTABLE".equals(input.assessment()) && input.maxIterations() > 1) {
            plans.add(Map.of(
                    "candidateId", "candidate-2",
                    "objective", input.objective(),
                    "status", "MANUAL_REQUIRED",
                    "reason", "当前仍有未满足的结构或显式 validation 条件，改为人工设计或补充对应验证。"));
        }
        return List.copyOf(plans);
    }

    private Map<String, Object> approvalBoundary(Input input,
                                                 List<String> allowedTools) {
        Object provided = input.request().get("approvalBoundary");
        if (provided instanceof Map<?, ?> map) return copy(map);
        String requestedMaxRisk = normalizeRisk(text(
                input.request().get("maxRiskLevel"), ""));
        String maxRiskLevel = requestedMaxRisk.isBlank()
                ? normalizeRiskOrDefault(input.riskLevel(), "MEDIUM")
                : maxRisk(requestedMaxRisk, input.riskLevel());
        return new LinkedHashMap<>(Map.of(
                "objectiveBoundary", text(firstNonNull(
                        input.request().get("objective"),
                        input.request().get("question")), ""),
                "allowedMcpTools", input.request().getOrDefault("allowedTools", allowedTools),
                "allowedEffectTypes", approvedEffectTypes(input.mcpSteps()),
                "allowedEffectScopes", approvedEffectScopes(input.mcpSteps()),
                "maxRiskLevel", maxRiskLevel,
                "forbiddenEffects", List.of("DELETE_TARGET_RESOURCE", "UNKNOWN"),
                "rollbackRequirement", Map.of("mustNotDowngrade", true),
                "verificationRequirement", Map.of("mustNotDowngrade", true)));
    }

    private List<String> approvedEffectTypes(List<Map<String, Object>> mcpSteps) {
        List<String> fromSteps = mcpSteps.stream()
                .map(step -> ChangePackagePreparationOperation.normalizeEffectType(
                        text(step.get("effectType"), "UNKNOWN")))
                .filter(effect -> !effect.isBlank() && !"UNKNOWN".equals(effect))
                .distinct()
                .toList();
        return fromSteps.isEmpty() ? DEFAULT_EFFECT_TYPES : fromSteps;
    }

    private List<String> approvedEffectScopes(List<Map<String, Object>> mcpSteps) {
        List<String> scopes = new ArrayList<>(DEFAULT_EFFECT_SCOPES);
        for (Map<String, Object> step : mcpSteps) {
            String scope = text(step.get("effectScope"), "").toUpperCase(Locale.ROOT);
            if (!scope.isBlank() && !"UNKNOWN".equals(scope) && !scopes.contains(scope)) {
                scopes.add(scope);
            }
        }
        return List.copyOf(scopes);
    }

    private Map<String, Object> preferredPlan(Input input,
                                              List<Map<String, Object>> candidates) {
        Object provided = input.request().get("preferredPlan");
        if (provided instanceof Map<?, ?> map) return copy(map);
        Map<String, Object> selected = candidates.isEmpty()
                ? Map.of()
                : candidates.get(candidates.size() - 1);
        return new LinkedHashMap<>(Map.of(
                "selectedCandidateId", text(firstNonNull(
                        selected.get("candidateId"), selected.get("id")), ""),
                "steps", input.mcpSteps(),
                "recommendedOrder", "as_planned",
                "expectedBeforeState", "READ_LATEST_BEFORE_LAND",
                "expectedAfterState", "VERIFY_BY_CRITERIA"));
    }

    private Map<String, Object> adjustmentPolicy(Input input) {
        Object provided = input.request().get("adjustmentPolicy");
        if (provided instanceof Map<?, ?> map) return copy(map);
        return new LinkedHashMap<>(Map.of(
                "allowReadLatestState", true,
                "allowRetry", true,
                "maxRetry", 2,
                "allowEquivalentReadTool", true,
                "allowEquivalentWriteTool", false,
                "allowArgumentAdjustmentWithinBoundary", true,
                "allowSkipAlreadySatisfiedStep", true,
                "allowReorderIndependentSteps", true,
                "maxRiskLevelAfterAdjustment", normalizeRiskOrDefault(
                        input.riskLevel(), "MEDIUM")));
    }

    private List<Map<String, Object>> allowedLandingAdjustments(
            Map<String, Object> request) {
        Object provided = request.get("allowedLandingAdjustments");
        if (!(provided instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> data = copy(map);
                if (!text(data.get("type"), "").isBlank()) result.add(data);
            } else {
                String type = text(item, "");
                if (!type.isBlank()) {
                    result.add(Map.of(
                            "type", type,
                            "allowedFileScope", "APPROVED_CHANGED_FILES_ONLY",
                            "requiresRetest", true));
                }
            }
        }
        return List.copyOf(result);
    }

    private List<String> allowedToolsFromSteps(List<Map<String, Object>> mcpSteps) {
        return mcpSteps.stream()
                .flatMap(step -> java.util.stream.Stream.of(
                        text(step.get("toolName"), ""),
                        text(step.get("mcpId"), "")))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String normalizeRiskOrDefault(String risk, String fallback) {
        String normalized = normalizeRisk(risk);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String normalizeRisk(String risk) {
        String value = text(risk, "").toUpperCase(Locale.ROOT);
        return List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(value)
                ? value
                : "";
    }

    private String maxRisk(String left, String right) {
        String normalizedLeft = normalizeRiskOrDefault(left, "MEDIUM");
        String normalizedRight = normalizeRiskOrDefault(right, "MEDIUM");
        return riskRank(normalizedRight) > riskRank(normalizedLeft)
                ? normalizedRight
                : normalizedLeft;
    }

    private int riskRank(String risk) {
        return switch (normalizeRisk(risk)) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            case "CRITICAL" -> 4;
            default -> 0;
        };
    }

    private List<Object> listValue(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : List.of();
    }

    private Map<String, Object> copy(Map<?, ?> map) {
        Map<String, Object> data = new LinkedHashMap<>();
        map.forEach((key, value) -> data.put(String.valueOf(key), value));
        return data;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    record Input(String objective,
                 int maxIterations,
                 String assessment,
                 Map<String, Object> request,
                 List<Map<String, Object>> mcpSteps,
                 List<String> limitations,
                 String riskLevel) {
        Input {
            objective = objective == null ? "" : objective.trim();
            maxIterations = Math.max(1, maxIterations);
            assessment = assessment == null ? "" : assessment.trim();
            request = immutableMap(request);
            mcpSteps = mcpSteps == null ? List.of() : List.copyOf(mcpSteps);
            limitations = limitations == null ? List.of() : List.copyOf(limitations);
            riskLevel = riskLevel == null ? "" : riskLevel.trim();
        }
    }

    record Envelope(List<Map<String, Object>> candidatePlans,
                    Map<String, Object> approvalBoundary,
                    Map<String, Object> preferredPlan,
                    Map<String, Object> adjustmentPolicy,
                    List<Map<String, Object>> allowedLandingAdjustments,
                    List<String> allowedTools,
                    List<String> replanTriggers) {
        Envelope {
            candidatePlans = candidatePlans == null ? List.of() : List.copyOf(candidatePlans);
            approvalBoundary = immutableMap(approvalBoundary);
            preferredPlan = immutableMap(preferredPlan);
            adjustmentPolicy = immutableMap(adjustmentPolicy);
            allowedLandingAdjustments = allowedLandingAdjustments == null
                    ? List.of()
                    : List.copyOf(allowedLandingAdjustments);
            allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
            replanTriggers = replanTriggers == null ? List.of() : List.copyOf(replanTriggers);
        }
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
