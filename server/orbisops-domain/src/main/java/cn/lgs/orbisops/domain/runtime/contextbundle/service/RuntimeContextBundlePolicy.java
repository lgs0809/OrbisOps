package cn.lgs.orbisops.domain.runtime.contextbundle.service;

import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleLayerInput;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextCanarySkillSnapshot;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextSkillSelection;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextToolPolicySnapshot;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextToolsetSnapshot;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RuntimeContextBundlePolicy {

    public Map<String, Object> assembleBase(RuntimeContextBundleLayerInput input) {
        if (input == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_INPUT_REQUIRED");
        Map<String, Object> metadata = input.metadata();
        String memoryHash = input.memoryContext().isBlank() ? "" : hashObject(input.memoryContext());
        List<String> memoryRefs = memoryHash.isBlank()
                ? List.of()
                : List.of("session:" + input.sessionId(), "project:" + input.projectId());
        String memorySummary = abbreviate(input.memoryContext(), 1200);

        Map<String, Object> taskContext = map(
                "intent", text(metadata.get("scene")),
                "goal", input.query(),
                "userRequest", input.query(),
                "explicitConstraints", value(metadata, "explicitConstraints", List.of()),
                "pendingActions", value(metadata, "pendingActions", List.of()));
        Map<String, Object> conversationContext = map(
                "sessionId", input.sessionId(),
                "summary", text(metadata.get("conversationSummary")),
                "recentMessagesRef", input.sessionId().isBlank() ? "" : "session:" + input.sessionId());
        Map<String, Object> memoryContext = map(
                "refs", memoryRefs,
                "hash", memoryHash,
                "summary", memorySummary,
                "injectionVersion", "context-bundle-v1");
        Map<String, Object> projectRuntimeContext = map(
                "projectId", input.projectId(),
                "serviceId", text(metadata.get("serviceId")),
                "environment", text(metadata.get("environment")),
                "resourceBindings", value(metadata, "resourceBindings", List.of()),
                "repositoryRefs", value(metadata, "repositoryRefs", List.of()),
                "runtimeSummary", text(metadata.get("projectRuntimeSummary")));
        Map<String, Object> toolObservationContext = map(
                "availableToolsets", value(metadata, "availableToolsets", List.of()),
                "blockedTools", value(metadata, "blockedTools", List.of()),
                "toolResultRefs", value(metadata, "toolResultRefs", List.of()),
                "lastToolResultsSummary", value(metadata, "lastToolResultsSummary", List.of()),
                "trustedProofRefs", value(metadata, "trustedProofRefs", List.of()),
                "summary", text(metadata.get("toolObservationSummary")));
        Map<String, Object> skillContext = map(
                "selected", value(metadata, "selectedSkills", List.of()),
                "skillRefs", value(metadata, "skillRefs", List.of()),
                "skillVersions", value(metadata, "skillVersions", List.of()),
                "skillHashes", value(metadata, "skillHashes", List.of()),
                "summary", text(metadata.get("skillSummary")));
        Map<String, Object> policyContext = map(
                "policyRefs", value(metadata, "policyRefs",
                        List.of("toolset-router", "change-package", "landing-runtime")),
                "toolPolicyRefs", value(metadata, "toolPolicyRefs", List.of()),
                "approvalPolicyRefs", value(metadata, "approvalPolicyRefs", List.of()),
                "summary", "Memory/Skill/Trace 不能覆盖 Policy、ToolsetRouter、Validation、Approval 或 LandingRuntime。");
        Map<String, Object> changePackageContext = map(
                "packageId", text(metadata.get("packageId")),
                "status", text(metadata.get("packageStatus")),
                "approvedVersion", value(metadata, "approvedVersion", ""),
                "approvedPackageHash", text(metadata.get("approvedPackageHash")),
                "summary", text(metadata.get("changePackageSummary")));

        Map<String, Object> layers = new LinkedHashMap<>();
        layers.put("taskContext", taskContext);
        layers.put("conversationContext", conversationContext);
        layers.put("memoryContext", memoryContext);
        layers.put("projectRuntimeContext", projectRuntimeContext);
        layers.put("toolObservationContext", toolObservationContext);
        layers.put("skillContext", skillContext);
        layers.put("policyContext", policyContext);
        layers.put("changePackageContext", changePackageContext);

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("contextBundleId", input.bundleId());
        bundle.put("runId", input.runId());
        bundle.put("sessionId", input.sessionId());
        bundle.put("projectId", input.projectId());
        bundle.put("agentId", input.agentId());
        int agentVersion = intValue(metadata.get("agentVersion"), 0);
        if (agentVersion > 0) bundle.put("agentVersion", agentVersion);
        bundle.put("actor", input.actor());
        bundle.put("layers", layers);
        bundle.putAll(layers);
        bundle.put("task", taskContext);
        bundle.put("conversation", conversationContext);
        bundle.put("memory", memoryContext);
        bundle.put("skills", skillContext);
        bundle.put("policy", policyContext);
        bundle.put("tools", toolObservationContext);
        bundle.put("changePackage", changePackageContext);
        bundle.put("compression", map(
                "compressorVersion", "v1",
                "tokenBudget", intValue(metadata.get("tokenBudget"), 12000),
                "truncatedRefs", value(metadata, "truncatedRefs", List.of())));
        bundle.put("memoryContextHash", memoryHash);
        bundle.put("compressedMemorySummary", memorySummary);
        bundle.put("memoryContextRefs", memoryRefs);
        bundle.put("memoryInjectionVersion", "context-bundle-v1");
        bundle.put("createdAt", input.createdAt().toString());
        return bundle;
    }

    public List<Map<String, Object>> selectSkillRefs(
            RuntimeContextSkillSelection selection,
            List<RuntimeContextCanarySkillSnapshot> canaryRefs,
            int selectedLimit) {
        if (selection == null) throw new IllegalArgumentException("RUNTIME_SKILL_SELECTION_REQUIRED");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> ref : selection.selectedRefs()) {
            if ("REQUESTED_ACTIVE_SKILL".equals(text(ref.get("selectedReason")))) result.add(copy(ref));
        }
        int normalLimit = Math.max(1, selectedLimit);
        if (result.size() < normalLimit && canaryRefs != null) {
            canaryRefs.stream()
                    .filter(ref -> ref != null)
                    .map(RuntimeContextCanarySkillSnapshot::canonicalView)
                    .filter(ref -> !duplicateSkill(result, ref))
                    .limit(1)
                    .map(this::copy)
                    .forEach(result::add);
        }
        int effectiveLimit = Math.max(normalLimit, result.size());
        for (Map<String, Object> ref : selection.selectedRefs()) {
            if ("REQUESTED_ACTIVE_SKILL".equals(text(ref.get("selectedReason")))) continue;
            if (result.size() >= effectiveLimit) break;
            if (!duplicateSkill(result, ref)) result.add(copy(ref));
        }
        return List.copyOf(result);
    }

    public List<Map<String, Object>> toolsetRefs(
            List<RuntimeContextToolsetSnapshot> toolsets) {
        if (toolsets == null || toolsets.isEmpty()) return List.of();
        List<Map<String, Object>> refs = new ArrayList<>();
        for (RuntimeContextToolsetSnapshot toolset : toolsets) {
            if (toolset == null) continue;
            Map<String, Object> canonical = toolset.canonicalView();
            refs.add(map(
                    "toolsetId", toolset.toolsetId(),
                    "toolsetVersion", 1,
                    "toolsetHash", hashObject(canonical),
                    "adapterType", toolset.adapterType(),
                    "tools", toolset.tools().stream()
                            .map(RuntimeContextToolPolicySnapshot::canonicalView)
                            .toList()));
        }
        return List.copyOf(refs);
    }

    public List<Map<String, Object>> policyRefs(
            List<RuntimeContextToolsetSnapshot> toolsets) {
        if (toolsets == null || toolsets.isEmpty()) return List.of();
        List<Map<String, Object>> refs = new ArrayList<>();
        for (RuntimeContextToolsetSnapshot toolset : toolsets) {
            if (toolset == null) continue;
            for (RuntimeContextToolPolicySnapshot tool : toolset.tools()) {
                Map<String, Object> canonical = tool.canonicalView();
                refs.add(map(
                        "policyId", toolset.toolsetId() + "/" + tool.toolName(),
                        "policyVersion", 1,
                        "policyHash", hashObject(canonical)));
            }
        }
        return List.copyOf(refs);
    }

    public String hashObject(Object value) {
        return CanonicalObjectHasher.sha256(
                value,
                Set.of("contextBundleHash", "createTime"));
    }

    private boolean duplicateSkill(List<Map<String, Object>> selected, Map<String, Object> candidate) {
        String skillId = text(candidate == null ? null : candidate.get("skillId"));
        return selected.stream().anyMatch(existing -> skillId.equals(text(existing.get("skillId"))));
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return new LinkedHashMap<>(source == null ? Map.of() : source);
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private Object value(Map<String, Object> source, String key, Object fallback) {
        Object value = source.get(key);
        return value == null ? fallback : value;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String abbreviate(String value, int maxLength) {
        String normalized = value == null ? "" : value;
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, Math.max(0, maxLength)) + "...";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
