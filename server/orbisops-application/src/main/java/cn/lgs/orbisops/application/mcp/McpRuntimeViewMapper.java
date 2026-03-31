package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpRoutingDecision;
import cn.lgs.orbisops.domain.mcp.model.McpRuntimeActivation;
import cn.lgs.orbisops.domain.mcp.model.McpToolCall;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyAdminRecord;
import cn.lgs.orbisops.domain.mcp.model.McpToolSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure query projection mapper shared by the narrow MCP query services. */
public final class McpRuntimeViewMapper {

    private final McpJsonCodec jsonCodec;

    public McpRuntimeViewMapper(McpJsonCodec jsonCodec) {
        if (jsonCodec == null) throw new IllegalArgumentException("MCP_JSON_CODEC_REQUIRED");
        this.jsonCodec = jsonCodec;
    }

    public Map<String, Object> runtimeCatalogView(Map<String, Object> tool) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("toolName", text(tool.get("toolName")));
        item.put("capability", text(tool.get("capability")));
        item.put("effectType", text(tool.get("effectType"), "UNKNOWN"));
        item.put("riskLevel", text(tool.get("riskLevel"), "HIGH"));
        item.put("readOnly", bool(tool.get("readOnly")));
        item.put("disclosureTier", text(tool.get("disclosureTier"), "EXTENSION"));
        String policyStatus = text(tool.get("policyStatus"), "ACTIVE");
        String reviewStatus = text(tool.get("reviewStatus"));
        item.put("policyStatus", policyStatus);
        item.put("reviewStatus", reviewStatus);
        String defaultDescription = "SYSTEM_VERIFIED".equalsIgnoreCase(reviewStatus)
                ? "平台内置只读工具，已通过系统验证；扩展工具按需激活后披露完整 schema。"
                : "HUMAN_REVIEWED".equalsIgnoreCase(reviewStatus)
                ? "已人工审核的项目工具；扩展工具按需激活后披露完整 schema。"
                : "项目工具；按当前 Tool Policy 状态决定是否可执行或激活。";
        item.put("description", text(tool.get("description"), defaultDescription));
        return immutable(item);
    }

    public Map<String, Object> runtimeToolSummary(McpToolPolicy policy) {
        Map<String, Object> metadata = map(jsonCodec.decode(policy.metadataJson()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("policyId", policy.policyId());
        data.put("mcpId", policy.mcpId());
        data.put("toolId", policy.toolId());
        data.put("toolName", policy.toolName());
        data.put("schemaHash", policy.schemaHash());
        data.put("effectType", policy.effectType());
        data.put("effectScope", policy.effectScope());
        data.put("mutability", policy.mutability());
        data.put("capability", policy.capability());
        data.put("riskLevel", policy.riskLevel().name());
        data.put("readOnly", policy.readOnly());
        data.put("policyStatus", policy.status().name());
        data.put("reviewStatus", policy.reviewStatus().name());
        data.put("investigateAllowed", policy.investigateAllowed());
        data.put("prepareAllowed", policy.prepareAllowed());
        data.put("requiresApprovedPackage", policy.requiresApprovedPackage());
        data.put("disclosureTier", text(metadata.get("disclosureTier"), "EXTENSION")
                .toUpperCase(Locale.ROOT));
        data.put("description", text(metadata.get("description")));
        return immutable(data);
    }

    public Map<String, Object> policyView(McpToolPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("MCP_TOOL_POLICY_REQUIRED");
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
        data.put("allowedActions", strings(jsonCodec.decode(policy.allowedActionsJson())));
        data.put("riskLevel", policy.riskLevel());
        data.put("readOnly", policy.readOnly());
        data.put("investigateAllowed", policy.investigateAllowed());
        data.put("prepareAllowed", policy.prepareAllowed());
        data.put("landAllowed", policy.landAllowed());
        data.put("requiresApprovedPackage", policy.requiresApprovedPackage());
        data.put("requiresHumanApproval", policy.requiresHumanApproval());
        data.put("requiresDryRun", policy.requiresDryRun());
        data.put("requiresRollbackPlan", policy.requiresRollbackPlan());
        data.put("argumentPolicyJson", policy.argumentPolicyJson());
        data.put("argumentPolicy", jsonCodec.decode(policy.argumentPolicyJson()));
        data.put("status", policy.status());
        data.put("policyStatus", policy.status());
        data.put("reviewStatus", policy.reviewStatus());
        data.put("reviewedBy", policy.reviewedBy());
        data.put("reviewedAt", policy.reviewedAt());
        data.put("suggestedBy", policy.suggestedBy());
        data.put("suggestedAt", policy.suggestedAt());
        data.put("metadataJson", policy.metadataJson());
        Object metadata = jsonCodec.decode(policy.metadataJson());
        data.put("metadata", metadata);
        data.put("createTime", policy.createTime());
        data.put("updateTime", policy.updateTime());
        Map<String, Object> metadataMap = map(metadata);
        data.put("disclosureTier", text(metadataMap.get("disclosureTier"), "EXTENSION")
                .toUpperCase(Locale.ROOT));
        data.put("description", text(metadataMap.get("description")));
        return immutable(data);
    }

    public Map<String, Object> policyAdminView(McpToolPolicyAdminRecord policy) {
        if (policy == null) throw new IllegalArgumentException("MCP_TOOL_POLICY_ADMIN_RECORD_REQUIRED");
        boolean invalid = policy.legacyInvalid();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", policy.id());
        data.put("policyId", policy.policyId());
        data.put("projectId", policy.projectId());
        data.put("mcpId", policy.mcpId());
        data.put("toolId", policy.toolId());
        data.put("toolName", policy.toolName());
        data.put("schemaHash", text(policy.schemaHash()));
        data.put("effectType", invalid ? "UNKNOWN" : text(policy.effectType(), "UNKNOWN"));
        data.put("effectScope", invalid ? "UNKNOWN" : text(policy.effectScope(), "UNKNOWN"));
        data.put("mutability", invalid ? "UNKNOWN" : text(policy.mutability(), "UNKNOWN"));
        data.put("capability", invalid ? "MUTATING" : text(policy.capability(), "UNKNOWN"));
        data.put("allowedActionsJson", invalid ? "[]" : text(policy.allowedActionsJson(), "[]"));
        data.put("allowedActions", invalid ? List.of() : strings(jsonCodec.decode(policy.allowedActionsJson())));
        data.put("riskLevel", invalid ? "HIGH" : text(policy.riskLevel(), "HIGH"));
        data.put("readOnly", invalid ? false : policy.readOnly());
        data.put("investigateAllowed", invalid ? false : policy.investigateAllowed());
        data.put("prepareAllowed", invalid ? false : policy.prepareAllowed());
        data.put("landAllowed", invalid ? false : policy.landAllowed());
        data.put("requiresApprovedPackage", invalid || policy.requiresApprovedPackage());
        data.put("requiresHumanApproval", invalid || policy.requiresHumanApproval());
        data.put("requiresDryRun", invalid || policy.requiresDryRun());
        data.put("requiresRollbackPlan", invalid || policy.requiresRollbackPlan());
        data.put("argumentPolicyJson", invalid ? "{}" : text(policy.argumentPolicyJson(), "{}"));
        data.put("argumentPolicy", invalid ? Map.of() : jsonCodec.decode(policy.argumentPolicyJson()));
        data.put("status", invalid ? "STALE" : text(policy.status(), "PENDING_REVIEW"));
        data.put("policyStatus", invalid ? "STALE" : text(policy.status(), "PENDING_REVIEW"));
        data.put("reviewStatus", invalid ? "UNREVIEWED" : text(policy.reviewStatus(), "UNREVIEWED"));
        data.put("reviewedBy", text(policy.reviewedBy()));
        data.put("reviewedAt", policy.reviewedAt());
        data.put("suggestedBy", text(policy.suggestedBy()));
        data.put("suggestedAt", policy.suggestedAt());
        data.put("metadataJson", text(policy.metadataJson(), "{}"));
        Object metadata = jsonCodec.decode(policy.metadataJson());
        data.put("metadata", metadata);
        data.put("createTime", policy.createTime());
        data.put("updateTime", policy.updateTime());
        data.put("legacyInvalid", invalid);
        data.put("invalidReason", policy.invalidReason());
        Map<String, Object> metadataMap = map(metadata);
        data.put("disclosureTier", text(metadataMap.get("disclosureTier"), "EXTENSION")
                .toUpperCase(Locale.ROOT));
        data.put("description", text(metadataMap.get("description")));
        return immutable(data);
    }

    public Map<String, Object> activationView(McpRuntimeActivation activation) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activationId", activation.activationId());
        data.put("projectId", activation.projectId());
        data.put("runId", activation.runId());
        data.put("sessionId", activation.sessionId());
        data.put("agentId", activation.agentId());
        data.put("mcpId", activation.mcpId());
        data.put("toolName", activation.toolName());
        data.put("schemaHash", activation.schemaHash());
        data.put("disclosureTier", activation.disclosureTier());
        data.put("status", activation.status());
        data.put("expiresAt", activation.expiresAt());
        data.put("metadataJson", activation.metadataJson());
        data.put("metadata", jsonCodec.decode(activation.metadataJson()));
        data.put("createTime", activation.createTime());
        data.put("updateTime", activation.updateTime());
        return immutable(data);
    }

    public Map<String, Object> snapshotView(McpToolSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("snapshotId", snapshot.snapshotId());
        data.put("projectId", snapshot.projectId());
        data.put("mcpId", snapshot.mcpId());
        data.put("toolId", snapshot.toolId());
        data.put("toolName", snapshot.toolName());
        data.put("schemaHash", snapshot.schemaHash());
        data.put("schemaJson", snapshot.schemaJson());
        data.put("schema", jsonCodec.decode(snapshot.schemaJson()));
        data.put("metadataJson", snapshot.metadataJson());
        data.put("metadata", jsonCodec.decode(snapshot.metadataJson()));
        data.put("metadataComplete", snapshot.metadataComplete());
        data.put("status", snapshot.status());
        data.put("createTime", snapshot.createTime());
        data.put("updateTime", snapshot.updateTime());
        return immutable(data);
    }

    public Map<String, Object> decisionView(McpRoutingDecision decision) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("decisionId", decision.decisionId());
        data.put("projectId", decision.projectId());
        data.put("agentId", decision.agentId());
        data.put("nodeId", decision.nodeId());
        data.put("runId", decision.runId());
        data.put("capability", decision.capability());
        data.put("requestJson", decision.requestJson());
        data.put("request", jsonCodec.decode(decision.requestJson()));
        data.put("selectedToolsJson", decision.selectedToolsJson());
        data.put("selectedTools", jsonCodec.decode(decision.selectedToolsJson()));
        data.put("reason", decision.reason());
        data.put("status", decision.status());
        data.put("createTime", decision.createTime());
        return immutable(data);
    }

    public Map<String, Object> toolCallView(McpToolCall call) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("callId", call.callId());
        data.put("projectId", call.projectId());
        data.put("agentId", call.agentId());
        data.put("nodeId", call.nodeId());
        data.put("runId", call.runId());
        data.put("toolId", call.toolId());
        data.put("mcpId", call.mcpId());
        data.put("toolName", call.toolName());
        data.put("riskLevel", call.riskLevel());
        data.put("readOnly", call.readOnly());
        data.put("status", call.status());
        data.put("inputJson", call.inputJson());
        data.put("input", jsonCodec.decode(call.inputJson()));
        data.put("outputJson", call.outputJson());
        data.put("output", jsonCodec.decode(call.outputJson()));
        data.put("durationMs", call.durationMs());
        data.put("errorMessage", call.errorMessage());
        data.put("createTime", call.createTime());
        return immutable(data);
    }

    private List<String> strings(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> {
                String normalized = text(item);
                if (!normalized.isBlank()) result.add(normalized);
            });
        }
        return List.copyOf(result);
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return immutable(result);
    }

    private Map<String, Object> immutable(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value);
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String text(Object value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }
}
