package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the frozen Preparation Method reference included in a ChangePackage draft. */
final class OpsPreparationMethodReferenceFactory {

    private static final Set<String> HASH_EXCLUSIONS = Set.of("preparationMethodHash");

    Map<String, Object> create(Map<String, Object> request,
                               OpsAgentDefinition agent) {
        if (agent == null) {
            throw new IllegalArgumentException("PREPARATION_AGENT_REQUIRED");
        }
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("preparationAgentId", text(agent.getAgentId(), ""));
        snapshot.put("preparationAgentVersion", agent.getVersion() == null ? 0 : agent.getVersion());
        snapshot.put("scopeType", text(agent.getProjectId(), "").isBlank() ? "GLOBAL" : "PROJECT");
        snapshot.put("projectId", text(agent.getProjectId(), ""));
        snapshot.put("name", text(agent.getName(), text(agent.getAgentId(), "")));
        snapshot.put("requiredTools", safeRequest.getOrDefault("allowedTools", List.of()));
        snapshot.put(
                "changePackageTemplate",
                "Preparation Agent 输出 ChangePackage，并由 validation/proof/approval/LandingRuntime 控制生产变更。");
        snapshot.put(
                "validationPlanTemplate",
                safeRequest.getOrDefault("verificationCriteria", List.of()));
        snapshot.put(
                "landingPreconditionsTemplate",
                safeRequest.getOrDefault("approvalBoundary", Map.of()));
        snapshot.put(
                "postCheckTemplate",
                safeRequest.getOrDefault("postCheckPlan", List.of()));
        snapshot.put(
                "rollbackTemplate",
                safeRequest.getOrDefault("rollbackPlan", Map.of("required", true)));
        snapshot.put(
                "riskGuidance",
                "Preparation method 仅提供方法，不替代 Policy / ToolsetRouter / Approval / LandingRuntime。");
        snapshot.put(
                "memoryUsageGuidance",
                "Memory 只能作为长期语境来源，不能作为权限或 proof。");
        snapshot.put(
                "preparationMethodSummary",
                text(
                        safeRequest.get("preparationMethodSummary"),
                        "使用 Preparation Agent 方法生成 ChangePackage 草案。"));
        snapshot.put(
                "preparationMethodHash",
                CanonicalObjectHasher.sha256(snapshot, HASH_EXCLUSIONS));
        return snapshot;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
