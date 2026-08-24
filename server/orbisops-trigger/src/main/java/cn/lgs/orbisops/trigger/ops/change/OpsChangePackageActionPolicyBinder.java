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
    private final org.springframework.beans.factory.ObjectProvider<OpsPreparePostCheckValidator> postChecks;

    public OpsChangePackageActionPolicyBinder(McpPolicyQueryService policies,
                                              ProjectMcpRuntimeDescriptorApplicationService descriptors) {
        this(policies, descriptors, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OpsChangePackageActionPolicyBinder(McpPolicyQueryService policies,
                                              ProjectMcpRuntimeDescriptorApplicationService descriptors,
                                              org.springframework.beans.factory.ObjectProvider<OpsPreparePostCheckValidator> postChecks) {
        if (policies == null) throw new IllegalArgumentException("MCP_POLICY_QUERY_SERVICE_REQUIRED");
        if (descriptors == null) throw new IllegalArgumentException("PROJECT_MCP_RUNTIME_DESCRIPTOR_SERVICE_REQUIRED");
        this.policies = policies;
        this.descriptors = descriptors;
        this.postChecks = postChecks;
    }

    public List<Map<String, Object>> bind(String projectId, Object rawActions) {
        List<McpToolPolicy> reviewed = reviewedPolicies(projectId);
        Map<String, McpToolPolicy> byIdentity = new LinkedHashMap<>();
        reviewed.forEach(policy -> byIdentity.putIfAbsent(key(policy.mcpId(), policy.toolName()), policy));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list(rawActions)) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> action = copy(raw);
            normalizePostChecks(action);
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
                action.put("targetEnvironment", reviewedResourceBinding(projectId, policy, "targetEnvironment"));
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
                action.put("resourceScope", reviewedResourceBinding(projectId, policy, "resourceScope"));
                action.put("arguments", authoritativeArguments(projectId, policy, action.get("arguments")));
                action.put("policyBound", true);
                if ("POST_APPROVAL_VALIDATION".equals(text(action.get("purpose")))) {
                    throw new IllegalArgumentException("CHANGE_PACKAGE_POST_CHECK_MUST_BE_ATTACHED: "
                            + "将审批后验证放入对应写操作的 postCheck.additionalChecks，并提供明确 expectedValues；普通读取不代表验收通过");
                }
                if (action.get("postCheck") instanceof Map<?, ?> check) {
                    validatePostChecks(projectId, action, copy(check), byIdentity);
                }
            }
            result.add(Collections.unmodifiableMap(new LinkedHashMap<>(action)));
        }
        return List.copyOf(result);
    }

    private void validatePostChecks(String projectId, Map<String, Object> action,
                                    Map<String, Object> check, Map<String, McpToolPolicy> policiesByIdentity) {
        var validator = postChecks == null ? null : postChecks.getIfAvailable();
        if (validator == null) throw new IllegalStateException("CHANGE_PACKAGE_POST_CHECK_VALIDATOR_REQUIRED");
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(check);
        if (check.containsKey("additionalChecks")) {
            if (!(check.get("additionalChecks") instanceof List<?> additional) || additional.size() > 15)
                throw new IllegalArgumentException("CHANGE_PACKAGE_ADDITIONAL_CHECKS_INVALID_OR_LIMIT_EXCEEDED");
            for (var item : additional) {
                if (!(item instanceof Map<?, ?> raw) || raw.containsKey("additionalChecks"))
                    throw new IllegalArgumentException("CHANGE_PACKAGE_ADDITIONAL_CHECKS_INVALID_OR_LIMIT_EXCEEDED");
                checks.add(copy(raw));
            }
        }
        for (var item : checks) {
            String toolset = text(item.get("toolsetId"));
            String mcp = toolset.startsWith("mcp.") ? toolset.substring(4) : "";
            validator.validate(projectId, item, policiesByIdentity.get(key(mcp, text(item.get("toolName")))),
                    text(action.get("resourceScope")));
        }
    }

    /** Accept the unambiguous sibling spelling before freezing hashes; never drop a check. */
    private void normalizePostChecks(Map<String, Object> action) {
        if (!action.containsKey("additionalChecks")) return;
        Object value = action.remove("additionalChecks");
        Object primary = action.get("postCheck");
        if (!(primary instanceof Map<?, ?> raw) || !(value instanceof List<?> siblings)) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_ADDITIONAL_CHECKS_REQUIRE_POST_CHECK_AND_LIST");
        }
        Map<String, Object> postCheck = copy(raw);
        List<Object> checks = new ArrayList<>();
        if (postCheck.containsKey("additionalChecks")) {
            if (!(postCheck.get("additionalChecks") instanceof List<?> nested)) {
                throw new IllegalArgumentException("CHANGE_PACKAGE_ADDITIONAL_CHECKS_MUST_BE_LIST");
            }
            checks.addAll(nested);
        }
        checks.addAll(siblings);
        if (checks.size() > 15 || checks.stream().anyMatch(check -> !(check instanceof Map<?, ?> map)
                || map.containsKey("additionalChecks") || !(map.get("expectedValues") instanceof Map<?, ?> expected)
                || expected.isEmpty())) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_ADDITIONAL_CHECKS_INVALID_OR_LIMIT_EXCEEDED");
        }
        postCheck.put("additionalChecks", List.copyOf(checks));
        action.put("postCheck", Collections.unmodifiableMap(postCheck));
    }

    public List<Map<String, Object>> proposalCatalog(String projectId) {
        return reviewedPolicies(projectId).stream()
                .filter(policy -> !policy.readOnly() || (policy.prepareAllowed()
                        && List.of("VALIDATE_ONLY", "DRY_RUN").contains(policy.effectType())))
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
        entry.put("targetEnvironment", reviewedResourceBinding(projectId, policy, "targetEnvironment"));
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
        entry.put("resourceScope", reviewedResourceBinding(projectId, policy, "resourceScope"));
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

    private String reviewedResourceBinding(String projectId, McpToolPolicy policy, String field) {
        String providerValue = "resourceScope".equals(field)
                ? authoritativeResourceScope(projectId, policy.mcpId())
                : authoritativeTargetEnvironment(projectId, policy.mcpId());
        Object raw = jsonObject(policy.argumentPolicyJson()).get("resourceBinding");
        if (!(raw instanceof Map<?, ?> binding)) return providerValue;
        String scope = text(binding.get("resourceScope"));
        String environment = text(binding.get("targetEnvironment"));
        if (scope.isBlank() || environment.isBlank()) {
            throw new IllegalArgumentException("MCP_REVIEWED_RESOURCE_BINDING_INCOMPLETE");
        }
        String reviewedValue = "resourceScope".equals(field) ? scope : environment;
        // Per-tool bindings support multi-resource MCP servers, but may never override a
        // conflicting project resource binding. Model-supplied action fields are already cleared.
        if (!providerValue.isBlank() && !providerValue.equals(reviewedValue)) {
            throw new IllegalArgumentException("MCP_REVIEWED_RESOURCE_BINDING_CONFLICT");
        }
        return reviewedValue;
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
