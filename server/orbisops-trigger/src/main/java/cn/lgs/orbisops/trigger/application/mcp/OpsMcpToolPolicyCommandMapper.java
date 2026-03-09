package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpCommands;
import cn.lgs.orbisops.application.mcp.McpToolPolicyPatch;
import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public final class OpsMcpToolPolicyCommandMapper {

    public McpCommands.PolicyMutation mutation(String projectId,
                                                String policyId,
                                                String actor,
                                                Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new McpCommands.PolicyMutation(projectId, policyId, actor,
                new McpToolPolicyPatch(
                        textField(safe, "mcpId"),
                        textField(safe, "toolId"),
                        aliasTextField(safe, "toolName", "remoteToolName"),
                        textField(safe, "schemaHash"),
                        aliasTextField(safe, "effectType", "effect_type"),
                        aliasTextField(safe, "effectScope", "effect_scope"),
                        textField(safe, "mutability"),
                        textField(safe, "capability"),
                        listField(safe, "allowedActions", "allowed_actions", "allowedActionsJson"),
                        riskField(safe, "riskLevel", "risk_level"),
                        booleanField(safe, "readOnly", "read_only"),
                        booleanField(safe, "investigateAllowed", "investigate_allowed"),
                        booleanField(safe, "prepareAllowed", "prepare_allowed"),
                        booleanField(safe, "landAllowed", "land_allowed"),
                        booleanField(safe, "requiresApprovedPackage", "requires_approved_package"),
                        booleanField(safe, "requiresHumanApproval", "requires_human_approval"),
                        booleanField(safe, "requiresDryRun", "requires_dry_run"),
                        booleanField(safe, "requiresRollbackPlan", "requires_rollback_plan"),
                        mapField(safe, "argumentPolicy", "argument_policy"),
                        aliasTextField(safe, "disclosureTier", "disclosure_tier"),
                        mapField(safe, "metadata"),
                        textField(safe, "reason")));
    }

    private McpToolPolicyPatch.Field<String> textField(Map<String, Object> source, String key) {
        return source.containsKey(key)
                ? McpToolPolicyPatch.Field.supplied(text(source.get(key)))
                : McpToolPolicyPatch.Field.absent();
    }

    private McpToolPolicyPatch.Field<String> aliasTextField(Map<String, Object> source,
                                                            String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) return textField(source, key);
        }
        return McpToolPolicyPatch.Field.absent();
    }

    private McpToolPolicyPatch.Field<Boolean> booleanField(Map<String, Object> source,
                                                           String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                return McpToolPolicyPatch.Field.supplied(bool(source.get(key)));
            }
        }
        return McpToolPolicyPatch.Field.absent();
    }

    private McpToolPolicyPatch.Field<McpRiskLevel> riskField(Map<String, Object> source,
                                                             String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                return McpToolPolicyPatch.Field.supplied(McpRiskLevel.require(text(source.get(key))));
            }
        }
        return McpToolPolicyPatch.Field.absent();
    }

    private McpToolPolicyPatch.Field<List<String>> listField(Map<String, Object> source,
                                                             String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                return McpToolPolicyPatch.Field.supplied(strings(source.get(key)));
            }
        }
        return McpToolPolicyPatch.Field.absent();
    }

    private McpToolPolicyPatch.Field<Map<String, Object>> mapField(Map<String, Object> source,
                                                                  String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                return McpToolPolicyPatch.Field.supplied(map(source.get(key)));
            }
        }
        return McpToolPolicyPatch.Field.absent();
    }

    private List<String> strings(Object value) {
        if (value == null) return List.of();
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> add(result, item));
        } else {
            String raw = text(value).replace('[', ' ').replace(']', ' ').replace('"', ' ');
            for (String item : raw.split("[,;，\\n]")) add(result, item);
        }
        return result.stream().distinct().toList();
    }

    private void add(List<String> target, Object value) {
        String normalized = text(value);
        if (!normalized.isBlank()) target.add(normalized);
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return Map.copyOf(result);
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value);
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "enabled".equalsIgnoreCase(normalized);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
