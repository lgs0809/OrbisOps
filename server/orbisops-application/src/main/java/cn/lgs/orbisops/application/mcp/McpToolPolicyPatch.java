package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record McpToolPolicyPatch(
        Field<String> mcpId,
        Field<String> toolId,
        Field<String> toolName,
        Field<String> schemaHash,
        Field<String> effectType,
        Field<String> effectScope,
        Field<String> mutability,
        Field<String> capability,
        Field<List<String>> allowedActions,
        Field<McpRiskLevel> riskLevel,
        Field<Boolean> readOnly,
        Field<Boolean> investigateAllowed,
        Field<Boolean> prepareAllowed,
        Field<Boolean> landAllowed,
        Field<Boolean> requiresApprovedPackage,
        Field<Boolean> requiresHumanApproval,
        Field<Boolean> requiresDryRun,
        Field<Boolean> requiresRollbackPlan,
        Field<Map<String, Object>> argumentPolicy,
        Field<String> disclosureTier,
        Field<Map<String, Object>> metadata,
        Field<String> reason) {

    public McpToolPolicyPatch {
        mcpId = field(mcpId);
        toolId = field(toolId);
        toolName = field(toolName);
        schemaHash = field(schemaHash);
        effectType = field(effectType);
        effectScope = field(effectScope);
        mutability = field(mutability);
        capability = field(capability);
        allowedActions = listField(allowedActions);
        riskLevel = field(riskLevel);
        readOnly = field(readOnly);
        investigateAllowed = field(investigateAllowed);
        prepareAllowed = field(prepareAllowed);
        landAllowed = field(landAllowed);
        requiresApprovedPackage = field(requiresApprovedPackage);
        requiresHumanApproval = field(requiresHumanApproval);
        requiresDryRun = field(requiresDryRun);
        requiresRollbackPlan = field(requiresRollbackPlan);
        argumentPolicy = mapField(argumentPolicy);
        disclosureTier = field(disclosureTier);
        metadata = mapField(metadata);
        reason = field(reason);
    }

    public static McpToolPolicyPatch empty() {
        Field<Object> absent = Field.absent();
        @SuppressWarnings("unchecked") Field<String> string = (Field<String>) (Field<?>) absent;
        @SuppressWarnings("unchecked") Field<Boolean> bool = (Field<Boolean>) (Field<?>) absent;
        @SuppressWarnings("unchecked") Field<McpRiskLevel> risk = (Field<McpRiskLevel>) (Field<?>) absent;
        @SuppressWarnings("unchecked") Field<List<String>> list = (Field<List<String>>) (Field<?>) absent;
        @SuppressWarnings("unchecked") Field<Map<String, Object>> map = (Field<Map<String, Object>>) (Field<?>) absent;
        return new McpToolPolicyPatch(string, string, string, string, string, string, string, string,
                list, risk, bool, bool, bool, bool, bool, bool, bool, bool,
                map, string, map, string);
    }

    private static <T> Field<T> field(Field<T> value) {
        return value == null ? Field.absent() : value;
    }

    private static Field<List<String>> listField(Field<List<String>> value) {
        if (value == null || !value.supplied()) return Field.absent();
        List<String> normalized = value.value() == null ? List.of() : value.value().stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        return Field.supplied(normalized);
    }

    private static Field<Map<String, Object>> mapField(Field<Map<String, Object>> value) {
        if (value == null || !value.supplied()) return Field.absent();
        Map<String, Object> normalized = value.value() == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(value.value()));
        return Field.supplied(normalized);
    }

    public record Field<T>(boolean supplied, T value) {
        public static <T> Field<T> absent() {
            return new Field<>(false, null);
        }

        public static <T> Field<T> supplied(T value) {
            return new Field<>(true, value);
        }

        public T orElse(T fallback) {
            return supplied ? value : fallback;
        }
    }
}
