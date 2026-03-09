package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.mcp.McpPolicyQueryService;
import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptor;
import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptorApplicationService;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsServiceControlResourceIdentity;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects reviewed MCP policy into ChangePackage action composition.
 *
 * <p>The model may choose an operation and provide business arguments, but it is not an authority
 * for tool identity/effect/schema metadata. Those fields are replaced from ACTIVE + HUMAN_REVIEWED
 * MCP policy before the package compiler evaluates the request. Unknown bindings remain fail-closed.</p>
 */
@Component
public class OpsChangePackageActionPolicyBinder {

    private final McpPolicyQueryService policies;
    private final ProjectMcpRuntimeDescriptorApplicationService descriptors;

    public OpsChangePackageActionPolicyBinder(McpPolicyQueryService policies,
                                              ProjectMcpRuntimeDescriptorApplicationService descriptors) {
        if (policies == null) throw new IllegalArgumentException("MCP_POLICY_QUERY_SERVICE_REQUIRED");
        if (descriptors == null) throw new IllegalArgumentException("PROJECT_MCP_RUNTIME_DESCRIPTOR_SERVICE_REQUIRED");
        this.policies = policies;
        this.descriptors = descriptors;
    }

    public List<Map<String, Object>> bind(String projectId, Object rawActions) {
        List<McpToolPolicy> reviewed = reviewedPolicies(projectId);
        Map<String, McpToolPolicy> byIdentity = new LinkedHashMap<>();
        reviewed.forEach(policy -> byIdentity.putIfAbsent(key(policy.mcpId(), policy.toolName()), policy));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list(rawActions)) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> action = copy(raw);
            String mcpId = text(action.get("mcpId"));
            String toolName = firstText(action, "toolName", "remoteToolName", "name");
            McpToolPolicy policy = byIdentity.get(key(mcpId, toolName));
            clearAuthorityFields(action);
            if (policy == null) {
                action.put("adapterType", "MCP");
                action.put("targetEnvironment", authoritativeTargetEnvironment(projectId, mcpId));
                action.put("effectType", "UNKNOWN");
                action.put("effectScope", "UNKNOWN");
                action.put("mutability", "UNKNOWN");
                action.put("riskLevel", "HIGH");
                action.put("readOnly", false);
                action.put("writesTargetResource", true);
                action.put("requiresChangePackage", true);
                action.put("requiresApproval", true);
                action.put("policyBound", false);
            } else {
                action.put("mcpId", policy.mcpId());
                action.put("toolName", policy.toolName());
                action.put("remoteToolName", policy.toolName());
                action.put("adapterType", "MCP");
                action.put("targetEnvironment", authoritativeTargetEnvironment(projectId, policy.mcpId()));
                action.put("schemaHash", policy.schemaHash());
                action.put("policyId", policy.policyId());
                action.put("policyStatus", policy.status().name());
                action.put("reviewStatus", policy.reviewStatus().name());
                action.put("effectType", policy.effectType());
                action.put("effectScope", policy.effectScope());
                action.put("mutability", policy.mutability());
                action.put("riskLevel", policy.riskLevel().name());
                action.put("readOnly", policy.readOnly());
                action.put("writesTargetResource", writesTargetResource(policy));
                action.put("prepareAllowed", policy.prepareAllowed());
                action.put("landAllowed", policy.landAllowed());
                action.put("requiresChangePackage", policy.requiresApprovedPackage());
                action.put("requiresApproval", policy.requiresHumanApproval());
                action.put("requiresDryRun", policy.requiresDryRun());
                action.put("requiresRollbackPlan", policy.requiresRollbackPlan());
                action.put("resourceScope", authoritativeResourceScope(projectId, policy.mcpId()));
                action.put("arguments", authoritativeArguments(projectId, policy, action.get("arguments")));
                action.put("policyBound", true);
            }
            result.add(Collections.unmodifiableMap(new LinkedHashMap<>(action)));
        }
        return List.copyOf(result);
    }

    public List<Map<String, Object>> proposalCatalog(String projectId) {
        return reviewedPolicies(projectId).stream()
                .filter(policy -> !policy.readOnly())
                .map(policy -> catalogEntry(projectId, policy))
                .toList();
    }

    private List<McpToolPolicy> reviewedPolicies(String projectId) {
        return policies.policyModels(projectId, 1000).stream()
                .filter(McpToolPolicy::activeAndHumanReviewed)
                .toList();
    }

    private Map<String, Object> catalogEntry(String projectId, McpToolPolicy policy) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("mcpId", policy.mcpId());
        entry.put("toolName", policy.toolName());
        entry.put("adapterType", "MCP");
        entry.put("targetEnvironment", authoritativeTargetEnvironment(projectId, policy.mcpId()));
        entry.put("schemaHash", policy.schemaHash());
        entry.put("effectType", policy.effectType());
        entry.put("effectScope", policy.effectScope());
        entry.put("mutability", policy.mutability());
        entry.put("riskLevel", policy.riskLevel().name());
        entry.put("readOnly", policy.readOnly());
        entry.put("writesTargetResource", writesTargetResource(policy));
        entry.put("prepareAllowed", policy.prepareAllowed());
        entry.put("landAllowed", policy.landAllowed());
        entry.put("requiresApprovedPackage", policy.requiresApprovedPackage());
        entry.put("requiresHumanApproval", policy.requiresHumanApproval());
        entry.put("requiresDryRun", policy.requiresDryRun());
        entry.put("requiresRollbackPlan", policy.requiresRollbackPlan());
        entry.put("resourceScope", authoritativeResourceScope(projectId, policy.mcpId()));
        entry.put("argumentPolicy", jsonObject(policy.argumentPolicyJson()));
        Map<String, Object> fixedArguments = fixedArguments(projectId, policy.mcpId());
        if (!fixedArguments.isEmpty()) entry.put("fixedArguments", fixedArguments);
        if ("restart_service_dry_run".equals(policy.toolName())) {
            entry.put("expectedVersionSource", "OBSERVE_FROM_DRY_RUN");
        } else if ("restart_service".equals(policy.toolName())) {
            entry.put("expectedVersionSource", "FREEZE_FROM_PRE_APPROVAL_DRY_RUN");
        }
        entry.put("purpose", policy.prepareAllowed() && !policy.requiresApprovedPackage()
                ? "PRE_APPROVAL_VALIDATION"
                : policy.landAllowed() ? "APPROVED_LANDING_ACTION" : "CONTROLLED_ACTION");
        return Map.copyOf(entry);
    }

    private Map<String, Object> authoritativeArguments(String projectId,
                                                       McpToolPolicy policy,
                                                       Object rawArguments) {
        Map<String, Object> arguments = rawArguments instanceof Map<?, ?> raw
                ? copy(raw)
                : new LinkedHashMap<>();
        arguments.putAll(fixedArguments(projectId, policy.mcpId()));
        if ("restart_service_dry_run".equals(policy.toolName())) {
            // The PREPARE dry-run is the trusted version observation. A model-supplied version is
            // untrusted and can only create false CAS mismatches; the observed version is frozen
            // onto restart_service by OpsOwningPrepareValidationService after a PASSED proof.
            arguments.remove("expectedVersion");
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    private Map<String, Object> fixedArguments(String projectId, String mcpId) {
        ProjectMcpRuntimeDescriptor descriptor = descriptors.resolveEnabled(projectId, mcpId).orElse(null);
        if (descriptor == null || descriptor.resource() == null || descriptor.mcp() == null) return Map.of();
        if (!"service_control".equalsIgnoreCase(text(descriptor.mcp().resourceType()))) return Map.of();
        String service = OpsServiceControlResourceIdentity.serviceName(descriptor.resource().endpoint());
        return Map.of("service", service);
    }

    private String authoritativeResourceScope(String projectId, String mcpId) {
        ProjectMcpRuntimeDescriptor descriptor = descriptors.resolveEnabled(projectId, mcpId).orElse(null);
        return descriptor == null || descriptor.resource() == null
                ? ""
                : text(descriptor.resource().endpoint());
    }

    private String authoritativeTargetEnvironment(String projectId, String mcpId) {
        ProjectMcpRuntimeDescriptor descriptor = descriptors.resolveEnabled(projectId, mcpId).orElse(null);
        return descriptor == null || descriptor.resource() == null
                ? ""
                : text(descriptor.resource().environment());
    }

    private boolean writesTargetResource(McpToolPolicy policy) {
        if (policy == null || policy.readOnly()) return false;
        String effectType = text(policy.effectType()).toUpperCase(java.util.Locale.ROOT);
        if (List.of("VALIDATE_ONLY", "DRY_RUN", "MUTATE_EPHEMERAL", "MUTATE_TEST_RESOURCE").contains(effectType)) {
            return false;
        }
        String effectScope = text(policy.effectScope()).toUpperCase(java.util.Locale.ROOT);
        String mutability = text(policy.mutability()).toUpperCase(java.util.Locale.ROOT);
        return policy.requiresApprovedPackage()
                || "PRODUCTION".equals(effectScope)
                || "TARGET_RESOURCE_WRITE".equals(effectScope)
                || "PROD_MUTATING".equals(mutability);
    }

    private void clearAuthorityFields(Map<String, Object> action) {
        for (String key : List.of(
                "adapterType", "targetEnvironment", "schemaHash", "policyId", "policyStatus", "reviewStatus",
                "policyBound", "resourceScope", "effectType", "effectScope", "mutability", "riskLevel",
                "readOnly", "writesTargetResource", "prepareAllowed", "landAllowed", "requiresChangePackage",
                "requiresApproval", "requiresDryRun", "requiresRollbackPlan")) {
            action.remove(key);
        }
    }

    private Map<String, Object> jsonObject(String value) {
        try {
            if (value == null || value.isBlank()) return Map.of();
            Object parsed = JSON.parse(value);
            if (!(parsed instanceof Map<?, ?> raw)) return Map.of();
            return Map.copyOf(copy(raw));
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private List<?> list(Object value) {
        return value instanceof List<?> values ? values : List.of();
    }

    private Map<String, Object> copy(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String key(String mcpId, String toolName) {
        return text(mcpId) + "\n" + text(toolName);
    }

    private String firstText(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            String value = text(source.get(key));
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
