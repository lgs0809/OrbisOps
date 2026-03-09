package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.domain.mcp.model.McpSchemaExposureTier;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReview;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class McpPolicyCommandModelMapper {

    private final McpJsonCodec jsonCodec;
    private final Clock clock;

    McpPolicyCommandModelMapper(McpJsonCodec jsonCodec, Clock clock) {
        if (jsonCodec == null) throw new IllegalArgumentException("MCP_JSON_CODEC_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("MCP_POLICY_CLOCK_REQUIRED");
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    McpToolPolicy createManual(String projectId,
                               String policyId,
                               String actor,
                               McpToolPolicyPatch patch) {
        String mcpId = required(patch.mcpId().value(), "MCP tool policy 必须提供 mcpId");
        String toolName = required(patch.toolName().value(), "MCP tool policy 必须提供 toolName");
        String schemaHash = required(patch.schemaHash().value(), "MCP_TOOL_POLICY_SCHEMA_HASH_REQUIRED");
        boolean readOnly = patch.readOnly().orElse(false);
        List<String> actions = patch.allowedActions().orElse(List.of());
        boolean mutating = !readOnly || mutatingActions(actions);
        McpRiskLevel risk = patch.riskLevel().orElse(McpRiskLevel.HIGH);
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> metadata = metadata(patch, Map.of());
        return new McpToolPolicy(
                0L, policyId, projectId, mcpId,
                patch.toolId().orElse(""), toolName, schemaHash,
                upper(patch.effectType().orElse(readOnly ? "READ_EXTERNAL_STATE" : "UNKNOWN"), "UNKNOWN"),
                upper(patch.effectScope().orElse(readOnly ? "TARGET_RESOURCE_READ" : "UNKNOWN"), "UNKNOWN"),
                upper(patch.mutability().orElse(mutating ? "MUTATING" : "READ_ONLY"), "UNKNOWN"),
                upper(patch.capability().orElse(mutating ? "MUTATING" : "READ_ONLY"), "UNKNOWN"),
                encode(actions.isEmpty() ? List.of("UNKNOWN_MUTATING") : actions),
                risk,
                readOnly,
                patch.investigateAllowed().orElse(readOnly),
                patch.prepareAllowed().orElse(false),
                patch.landAllowed().orElse(readOnly && !highRisk(risk.name())),
                patch.requiresApprovedPackage().orElse(mutating || highRisk(risk.name())),
                patch.requiresHumanApproval().orElse(mutating || highRisk(risk.name())),
                patch.requiresDryRun().orElse(mutating),
                patch.requiresRollbackPlan().orElse(mutating),
                encode(patch.argumentPolicy().orElse(Map.of())),
                McpToolPolicyStatus.ACTIVE,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                actor,
                now,
                "",
                null,
                encode(metadata),
                null,
                null);
    }

    McpToolPolicy approve(McpToolPolicy before,
                          String actor,
                          McpToolPolicyPatch patch) {
        if (before == null) throw new IllegalArgumentException("MCP_TOOL_POLICY_REQUIRED");
        boolean readOnly = patch.readOnly().orElse(before.readOnly());
        List<String> actions = patch.allowedActions().orElse(strings(before.allowedActionsJson()));
        McpRiskLevel risk = patch.riskLevel().orElse(before.riskLevel());
        Map<String, Object> metadata = metadata(patch, jsonMap(before.metadataJson()));
        return new McpToolPolicy(
                before.id(), before.policyId(), before.projectId(),
                required(patch.mcpId().orElse(before.mcpId()), "MCP tool policy 必须提供 mcpId"),
                patch.toolId().orElse(before.toolId()),
                required(patch.toolName().orElse(before.toolName()), "MCP tool policy 必须提供 toolName"),
                required(patch.schemaHash().orElse(before.schemaHash()), "MCP_TOOL_POLICY_SCHEMA_HASH_REQUIRED"),
                upper(patch.effectType().orElse(before.effectType()), before.effectType()),
                upper(patch.effectScope().orElse(before.effectScope()), before.effectScope()),
                upper(patch.mutability().orElse(before.mutability()), before.mutability()),
                upper(patch.capability().orElse(before.capability()), before.capability()),
                encode(actions), risk, readOnly,
                patch.investigateAllowed().orElse(before.investigateAllowed()),
                patch.prepareAllowed().orElse(before.prepareAllowed()),
                patch.landAllowed().orElse(before.landAllowed()),
                patch.requiresApprovedPackage().orElse(before.requiresApprovedPackage()),
                patch.requiresHumanApproval().orElse(before.requiresHumanApproval()),
                patch.requiresDryRun().orElse(before.requiresDryRun()),
                patch.requiresRollbackPlan().orElse(before.requiresRollbackPlan()),
                encode(patch.argumentPolicy().orElse(decodeMap(before.argumentPolicyJson()))),
                McpToolPolicyStatus.ACTIVE,
                McpToolPolicyReviewStatus.HUMAN_REVIEWED,
                actor,
                LocalDateTime.now(clock),
                before.suggestedBy(), before.suggestedAt(),
                encode(metadata), before.createTime(), before.updateTime());
    }

    McpToolPolicyReview review(McpToolPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("MCP_TOOL_POLICY_REQUIRED");
        Map<String, Object> metadata = jsonMap(policy.metadataJson());
        return new McpToolPolicyReview(
                policy.effectType(), policy.effectScope(), policy.mutability(),
                strings(policy.allowedActionsJson()), policy.readOnly(), policy.investigateAllowed(),
                policy.requiresApprovedPackage(), policy.requiresHumanApproval(),
                policy.riskLevel(),
                McpSchemaExposureTier.require(text(metadata.get("disclosureTier"), "EXTENSION")));
    }

    Map<String, Object> metadataWithReviewReason(McpToolPolicy policy, McpToolPolicyPatch patch) {
        Map<String, Object> metadata = jsonMap(policy == null ? null : policy.metadataJson());
        if (patch != null && patch.reason().supplied()) {
            metadata.put("reviewReason", text(patch.reason().value(), ""));
        }
        return metadata;
    }

    private Map<String, Object> metadata(McpToolPolicyPatch patch, Map<String, Object> current) {
        Map<String, Object> metadata = current == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(current);
        if (patch.metadata().supplied()) {
            metadata.putAll(patch.metadata().value());
        }
        metadata.put("disclosureTier",
                upper(patch.disclosureTier().orElse(text(metadata.get("disclosureTier"), "EXTENSION")),
                        "EXTENSION"));
        return metadata;
    }

    private Map<String, Object> decodeMap(String json) {
        return jsonMap(json);
    }

    private String required(String value, String message) {
        String normalized = text(value, "");
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String upper(String value, String fallback) {
        return text(value, fallback).toUpperCase(Locale.ROOT);
    }

    McpToolPolicyReview review(Map<String, Object> policy) {
        Map<String, Object> safe = policy == null ? Map.of() : policy;
        Map<String, Object> metadata = jsonMap(safe.get("metadata"));
        String disclosureTier = text(firstNonNull(safe.get("disclosureTier"), safe.get("disclosure_tier"),
                metadata.get("disclosureTier")), "EXTENSION").toUpperCase(Locale.ROOT);
        return new McpToolPolicyReview(
                text(firstNonNull(safe.get("effectType"), safe.get("effect_type")), "UNKNOWN"),
                text(firstNonNull(safe.get("effectScope"), safe.get("effect_scope")), "UNKNOWN"),
                text(safe.get("mutability"), "UNKNOWN"),
                strings(firstNonNull(safe.get("allowedActions"), safe.get("allowed_actions"),
                        safe.get("allowedActionsJson"))),
                bool(firstNonNull(safe.get("readOnly"), safe.get("read_only")), false),
                bool(firstNonNull(safe.get("investigateAllowed"), safe.get("investigate_allowed")), false),
                bool(firstNonNull(safe.get("requiresApprovedPackage"),
                        safe.get("requires_approved_package")), false),
                bool(firstNonNull(safe.get("requiresHumanApproval"),
                        safe.get("requires_human_approval")), false),
                McpRiskLevel.failClosed(text(firstNonNull(
                        safe.get("riskLevel"), safe.get("risk_level")), "HIGH")),
                McpSchemaExposureTier.require(disclosureTier));
    }

    Map<String, Object> normalize(String projectId,
                                  String mcpId,
                                  String toolName,
                                  String toolId,
                                  String schemaHash,
                                  Map<String, Object> source,
                                  String policyStatus,
                                  String operator) {
        Map<String, Object> safe = source == null ? Map.of() : source;
        boolean readOnly = bool(firstNonNull(safe.get("readOnly"), safe.get("read_only")), false);
        String riskLevel = text(firstNonNull(safe.get("riskLevel"), safe.get("risk_level")), "HIGH")
                .toUpperCase(Locale.ROOT);
        List<String> allowedActions = strings(firstNonNull(
                safe.get("allowedActions"), safe.get("allowed_actions"), safe.get("allowedActionsJson")));
        boolean mutating = !readOnly || mutatingActions(allowedActions);
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("policyId", text(safe.get("policyId"),
                ("mcp-policy-" + projectId + "-" + mcpId + "-" + toolName + "-" + text(schemaHash, "latest"))
                        .replaceAll("[^a-zA-Z0-9_.:-]", "-")));
        policy.put("projectId", projectId);
        policy.put("mcpId", mcpId);
        policy.put("toolId", toolId);
        policy.put("toolName", toolName);
        policy.put("schemaHash", text(schemaHash, ""));
        policy.put("effectType", text(firstNonNull(safe.get("effectType"), safe.get("effect_type")),
                readOnly ? "READ_EXTERNAL_STATE" : "UNKNOWN").toUpperCase(Locale.ROOT));
        policy.put("effectScope", text(firstNonNull(safe.get("effectScope"), safe.get("effect_scope")),
                readOnly ? "TARGET_RESOURCE_READ" : "UNKNOWN").toUpperCase(Locale.ROOT));
        policy.put("mutability", text(safe.get("mutability"), mutating ? "MUTATING" : "READ_ONLY")
                .toUpperCase(Locale.ROOT));
        policy.put("capability", text(safe.get("capability"), mutating ? "MUTATING" : "READ_ONLY")
                .toUpperCase(Locale.ROOT));
        policy.put("allowedActions", allowedActions.isEmpty() ? List.of("UNKNOWN_MUTATING") : allowedActions);
        policy.put("riskLevel", validRisk(riskLevel));
        policy.put("readOnly", readOnly);
        policy.put("investigateAllowed", bool(firstNonNull(
                safe.get("investigateAllowed"), safe.get("investigate_allowed")), readOnly));
        policy.put("prepareAllowed", bool(firstNonNull(
                safe.get("prepareAllowed"), safe.get("prepare_allowed")), false));
        policy.put("landAllowed", bool(firstNonNull(
                safe.get("landAllowed"), safe.get("land_allowed")), readOnly && !highRisk(riskLevel)));
        policy.put("requiresApprovedPackage", bool(firstNonNull(
                safe.get("requiresApprovedPackage"), safe.get("requires_approved_package")),
                mutating || highRisk(riskLevel)));
        policy.put("requiresHumanApproval", bool(firstNonNull(
                safe.get("requiresHumanApproval"), safe.get("requires_human_approval")),
                mutating || highRisk(riskLevel)));
        policy.put("requiresDryRun", bool(firstNonNull(
                safe.get("requiresDryRun"), safe.get("requires_dry_run")), mutating));
        policy.put("requiresRollbackPlan", bool(firstNonNull(
                safe.get("requiresRollbackPlan"), safe.get("requires_rollback_plan")), mutating));
        policy.put("argumentPolicy", firstNonNull(
                safe.get("argumentPolicy"), safe.get("argument_policy"), safe.get("argumentPolicyJson"), Map.of()));
        policy.put("status", text(safe.get("status"), operator.isBlank() ? "PENDING_REVIEW" : "ACTIVE")
                .toUpperCase(Locale.ROOT));
        policy.put("policyStatus", text(policyStatus, text(policy.get("status"), "PENDING_REVIEW")));
        policy.put("reviewStatus", text(firstNonNull(safe.get("reviewStatus"), safe.get("review_status")),
                operator.isBlank() ? "UNREVIEWED" : "HUMAN_REVIEWED").toUpperCase(Locale.ROOT));
        policy.put("reviewedBy", text(firstNonNull(safe.get("reviewedBy"), safe.get("reviewed_by")), operator));
        Map<String, Object> metadata = jsonMap(safe.get("metadata"));
        metadata.put("disclosureTier", text(firstNonNull(
                safe.get("disclosureTier"), safe.get("disclosure_tier"), metadata.get("disclosureTier")),
                "EXTENSION").toUpperCase(Locale.ROOT));
        policy.put("metadata", metadata);
        return policy;
    }

    McpToolPolicy record(Map<String, Object> policy) {
        String reviewStatus = text(policy.get("reviewStatus"), "UNREVIEWED").toUpperCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now(clock);
        return new McpToolPolicy(0,
                text(policy.get("policyId"), ""), text(policy.get("projectId"), ""),
                text(policy.get("mcpId"), ""), text(policy.get("toolId"), ""),
                text(policy.get("toolName"), ""), text(policy.get("schemaHash"), ""),
                text(policy.get("effectType"), "UNKNOWN"), text(policy.get("effectScope"), "UNKNOWN"),
                text(policy.get("mutability"), "UNKNOWN"), text(policy.get("capability"), "UNKNOWN"),
                encode(policy.getOrDefault("allowedActions", List.of())),
                McpRiskLevel.failClosed(text(policy.get("riskLevel"), "HIGH")),
                bool(policy.get("readOnly"), false),
                bool(policy.get("investigateAllowed"), false), bool(policy.get("prepareAllowed"), false),
                bool(policy.get("landAllowed"), false), bool(policy.get("requiresApprovedPackage"), true),
                bool(policy.get("requiresHumanApproval"), true), bool(policy.get("requiresDryRun"), false),
                bool(policy.get("requiresRollbackPlan"), true),
                encode(policy.getOrDefault("argumentPolicy", Map.of())),
                McpToolPolicyStatus.require(text(policy.get("status"), "PENDING_REVIEW")),
                McpToolPolicyReviewStatus.require(reviewStatus),
                text(policy.get("reviewedBy"), ""), "HUMAN_REVIEWED".equals(reviewStatus) ? now : null,
                reviewStatus.contains("SUGGESTED") ? "system" : "",
                reviewStatus.contains("SUGGESTED") ? now : null,
                encode(policy.getOrDefault("metadata", Map.of())), null, null);
    }

    McpToolPolicyProjection projection(McpToolPolicy policy) {
        if (policy == null) return McpToolPolicyProjection.missing();
        Map<String, Object> metadata = jsonMap(policy.metadataJson());
        return new McpToolPolicyProjection(
                policy.policyId(), policy.status(), policy.reviewStatus(),
                policy.effectType(), policy.effectScope(), policy.mutability(), policy.capability(),
                strings(policy.allowedActionsJson()), policy.riskLevel(), policy.readOnly(),
                policy.investigateAllowed(), policy.prepareAllowed(), policy.landAllowed(),
                policy.requiresApprovedPackage(), policy.requiresHumanApproval(),
                policy.requiresDryRun(), policy.requiresRollbackPlan(),
                McpSchemaExposureTier.require(text(metadata.get("disclosureTier"), "EXTENSION")),
                jsonMap(policy.argumentPolicyJson()));
    }

    McpToolPolicyProjection projection(Map<String, Object> policy) {
        if (policy == null || policy.isEmpty()) return McpToolPolicyProjection.missing();
        return new McpToolPolicyProjection(
                text(policy.get("policyId"), ""),
                McpToolPolicyStatus.require(text(firstNonNull(
                        policy.get("policyStatus"), policy.get("status")), "MISSING")),
                McpToolPolicyReviewStatus.require(text(policy.get("reviewStatus"), "UNREVIEWED")),
                text(policy.get("effectType"), "UNKNOWN"),
                text(policy.get("effectScope"), "UNKNOWN"),
                text(policy.get("mutability"), "UNKNOWN"),
                text(policy.get("capability"), "UNKNOWN"),
                strings(policy.get("allowedActions")),
                McpRiskLevel.failClosed(text(policy.get("riskLevel"), "HIGH")),
                bool(policy.get("readOnly"), false),
                bool(policy.get("investigateAllowed"), false),
                bool(policy.get("prepareAllowed"), false),
                bool(policy.get("landAllowed"), false),
                bool(policy.get("requiresApprovedPackage"), true),
                bool(policy.get("requiresHumanApproval"), true),
                bool(policy.get("requiresDryRun"), false),
                bool(policy.get("requiresRollbackPlan"), true),
                McpSchemaExposureTier.require(text(policy.get("disclosureTier"), "EXTENSION")),
                jsonMap(policy.get("argumentPolicy")));
    }

    Map<String, Object> view(McpToolPolicy policy) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", policy.id());
        data.put("policyId", policy.policyId());
        data.put("projectId", policy.projectId());
        data.put("mcpId", policy.mcpId());
        data.put("toolId", policy.toolId());
        data.put("toolName", policy.toolName());
        data.put("schemaHash", policy.schemaHash());
        data.put("effectType", policy.effectType());
        data.put("effectScope", policy.effectScope());
        data.put("mutability", policy.mutability());
        data.put("capability", policy.capability());
        data.put("allowedActionsJson", policy.allowedActionsJson());
        data.put("riskLevel", policy.riskLevel().name());
        data.put("readOnly", policy.readOnly());
        data.put("investigateAllowed", policy.investigateAllowed());
        data.put("prepareAllowed", policy.prepareAllowed());
        data.put("landAllowed", policy.landAllowed());
        data.put("requiresApprovedPackage", policy.requiresApprovedPackage());
        data.put("requiresHumanApproval", policy.requiresHumanApproval());
        data.put("requiresDryRun", policy.requiresDryRun());
        data.put("requiresRollbackPlan", policy.requiresRollbackPlan());
        data.put("argumentPolicyJson", policy.argumentPolicyJson());
        data.put("status", policy.status().name());
        data.put("reviewStatus", policy.reviewStatus().name());
        data.put("reviewedBy", policy.reviewedBy());
        data.put("reviewedAt", policy.reviewedAt());
        data.put("suggestedBy", policy.suggestedBy());
        data.put("suggestedAt", policy.suggestedAt());
        data.put("metadataJson", policy.metadataJson());
        data.put("createTime", policy.createTime());
        data.put("updateTime", policy.updateTime());
        data.put("policyStatus", text(data.get("status"), "ACTIVE"));
        data.put("allowedActions", strings(data.get("allowedActionsJson")));
        data.put("readOnly", bool(data.get("readOnly"), false));
        data.put("investigateAllowed", bool(data.get("investigateAllowed"), false));
        data.put("prepareAllowed", bool(data.get("prepareAllowed"), false));
        data.put("landAllowed", bool(data.get("landAllowed"), false));
        data.put("requiresApprovedPackage", bool(data.get("requiresApprovedPackage"), true));
        data.put("requiresHumanApproval", bool(data.get("requiresHumanApproval"), true));
        data.put("requiresDryRun", bool(data.get("requiresDryRun"), false));
        data.put("requiresRollbackPlan", bool(data.get("requiresRollbackPlan"),
                !bool(data.get("readOnly"), false)));
        data.put("argumentPolicy", decode(policy.argumentPolicyJson()));
        data.put("metadata", decode(policy.metadataJson()));
        Map<String, Object> metadata = jsonMap(data.get("metadata"));
        data.put("disclosureTier", text(firstNonNull(
                data.get("disclosureTier"), metadata.get("disclosureTier")), "EXTENSION").toUpperCase(Locale.ROOT));
        data.put("description", text(firstNonNull(data.get("description"), metadata.get("description")), ""));
        return data;
    }

    Map<String, Object> jsonMap(Object value) {
        if (value instanceof Map<?, ?> map) return mapFrom(map);
        String serialized = text(value, "");
        if (serialized.isBlank()) return new LinkedHashMap<>();
        Object decoded = decode(serialized);
        return decoded instanceof Map<?, ?> map ? mapFrom(map) : new LinkedHashMap<>();
    }

    String encode(Object value) {
        return jsonCodec.encode(value);
    }

    boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value, "");
        if (normalized.isBlank()) return fallback;
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized) || "y".equalsIgnoreCase(normalized);
    }

    String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private Object decode(String json) {
        if (json == null || json.isBlank()) return Map.of();
        Object decoded = jsonCodec.decode(json);
        return decoded == null ? Map.of() : decoded;
    }

    private Map<String, Object> mapFrom(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private List<String> strings(Object value) {
        Object source = value;
        if (value instanceof String text && !text.isBlank()) {
            String trimmed = text.trim();
            if (trimmed.startsWith("[")) source = decode(trimmed);
            else {
                List<String> values = new ArrayList<>();
                for (String item : trimmed.split("[,;\\s]+")) {
                    String normalized = item.trim();
                    if (!normalized.isBlank()) values.add(normalized);
                }
                return List.copyOf(values);
            }
        }
        if (source instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object item : iterable) {
                String normalized = text(item, "");
                if (!normalized.isBlank()) values.add(normalized);
            }
            return List.copyOf(values);
        }
        return List.of();
    }

    private boolean mutatingActions(List<String> allowedActions) {
        if (allowedActions == null || allowedActions.isEmpty()) return true;
        return allowedActions.stream().map(item -> item.toUpperCase(Locale.ROOT)).anyMatch(action ->
                action.contains("WRITE") || action.contains("UPDATE") || action.contains("DELETE")
                        || action.contains("INSERT") || action.contains("EXECUTE") || action.contains("RESTART")
                        || action.contains("CONFIG") || action.contains("MUTATE") || action.contains("UNKNOWN"));
    }

    private boolean highRisk(String riskLevel) {
        String risk = text(riskLevel, "").toUpperCase(Locale.ROOT);
        return "HIGH".equals(risk) || "CRITICAL".equals(risk);
    }

    private String validRisk(String riskLevel) {
        return highRisk(riskLevel) || "LOW".equals(riskLevel) || "MEDIUM".equals(riskLevel)
                ? riskLevel : "HIGH";
    }
}
