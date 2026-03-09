package cn.lgs.orbisops.domain.mcpexecution.service;

import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionConfig;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionDecision;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionTarget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class McpExecutionPolicy {

    public String requireToolName(McpExecutionRequest request) {
        String toolName = request.requestedToolName();
        if (toolName.isBlank()) {
            throw new IllegalArgumentException(
                    "渐进式 MCP 调用必须提供 toolName，或项目工具只授权一个远端工具");
        }
        return toolName;
    }

    public McpExecutionDecision authorize(McpExecutionConfig config, String toolName) {
        if (matches(config.blockedTools(), toolName)
                || (!config.allowedTools().isEmpty()
                && !matches(config.allowedTools(), toolName)
                && !matches(config.notificationTools(), toolName))) {
            return McpExecutionDecision.blocked(
                    "MCP_TOOL_NOT_AUTHORIZED",
                    "远端 MCP 工具未在当前项目授权范围内：" + toolName);
        }
        return McpExecutionDecision.allowed(Map.of());
    }

    public McpExecutionDecision policyDecision(Map<String, Object> schema, String toolName) {
        String status = value(schema.get("policyStatus"), value(schema.get("status"), "MISSING")).toUpperCase();
        String review = value(schema.get("reviewStatus"), "UNREVIEWED").toUpperCase();
        if (!"ACTIVE".equals(status)) {
            String reason = switch (status) {
                case "PENDING_REVIEW" -> "MCP_POLICY_PENDING_REVIEW";
                case "STALE" -> "MCP_POLICY_STALE";
                case "DISABLED", "REJECTED" -> "MCP_POLICY_DISABLED";
                default -> "MCP_POLICY_MISSING";
            };
            return McpExecutionDecision.blocked(
                    reason, "MCP 工具策略未处于 ACTIVE，tool=" + toolName);
        }
        if (!approvedReviewStatus(review)) {
            return McpExecutionDecision.blocked(
                    "MCP_POLICY_PENDING_REVIEW",
                    "MCP 工具策略未经过审核或系统验证，tool=" + toolName);
        }
        if ("UNKNOWN".equalsIgnoreCase(value(schema.get("effectType"), "UNKNOWN"))
                || "UNKNOWN".equalsIgnoreCase(value(schema.get("effectScope"), "UNKNOWN"))
                || "UNKNOWN".equalsIgnoreCase(value(schema.get("mutability"), "UNKNOWN"))) {
            return McpExecutionDecision.blocked(
                    "MCP_TOOL_EFFECT_UNKNOWN",
                    "MCP 工具效果未定级，禁止业务调用，tool=" + toolName);
        }
        return McpExecutionDecision.allowed(schema);
    }

    public McpExecutionTarget target(
            McpExecutionConfig config,
            String toolName,
            Map<String, Object> schema) {
        boolean active = "ACTIVE".equalsIgnoreCase(
                value(schema.get("policyStatus"), value(schema.get("status"), "")))
                && approvedReviewStatus(value(schema.get("reviewStatus"), ""));
        boolean readOnly = active && bool(schema.get("readOnly"), false);
        String effectType = normalizeEffectType(schema.get("effectType"));
        String effectScope = value(schema.get("effectScope"), "UNKNOWN").toUpperCase();
        String mutability = value(schema.get("mutability"), "UNKNOWN").toUpperCase();
        boolean validationMode = List.of(
                        "READ_EXTERNAL_STATE", "NO_EFFECT", "VALIDATE_ONLY", "DRY_RUN",
                        "MUTATE_EPHEMERAL", "MUTATE_TEST_RESOURCE")
                .contains(effectType)
                && !List.of("PRODUCTION", "TARGET_RESOURCE_WRITE").contains(effectScope)
                && !"PROD_MUTATING".equals(mutability)
                && !"DESTRUCTIVE".equals(mutability);
        String risk = value(schema.get("riskLevel"), "HIGH").toUpperCase();
        boolean highRisk = "HIGH".equals(risk) || "CRITICAL".equals(risk);
        boolean targetWrite = !validationMode
                || "MUTATE_TARGET_RESOURCE".equals(effectType)
                || "EXECUTE_EXTERNAL_ACTION".equals(effectType)
                || "DELETE_TARGET_RESOURCE".equals(effectType)
                || "PRODUCTION".equals(effectScope)
                || "TARGET_RESOURCE_WRITE".equals(effectScope);
        boolean explicitPrepareValidation = bool(schema.get("prepareAllowed"), false)
                && List.of("VALIDATE_ONLY", "DRY_RUN", "MUTATE_EPHEMERAL", "MUTATE_TEST_RESOURCE")
                .contains(effectType);
        boolean lowRiskRead = readOnly && !highRisk
                && List.of("READ_EXTERNAL_STATE", "NO_EFFECT").contains(effectType);
        boolean requiresPackage = targetWrite
                || bool(schema.get("requiresApprovedPackage"), false)
                || (highRisk && !explicitPrepareValidation);
        if ((lowRiskRead || explicitPrepareValidation) && validationMode) {
            requiresPackage = false;
        }
        boolean writesTarget = targetWrite || (requiresPackage && !explicitPrepareValidation && !lowRiskRead);
        return new McpExecutionTarget(
                config.toolsetId(), toolName, config.mcpId(), "MCP", risk,
                readOnly, writesTarget, requiresPackage,
                targetWrite || bool(schema.get("requiresHumanApproval"), false), active);
    }

    public Map<String, Object> blockedPayload(
            McpExecutionConfig config,
            String toolName,
            McpExecutionDecision decision) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "BLOCKED");
        payload.put("allowed", false);
        payload.put("decision", decision.decision());
        payload.put("reasonCode", decision.reasonCode());
        payload.put("message", decision.message());
        payload.put("retryable", decision.retryable());
        payload.put("terminal", decision.terminal());
        payload.put("toolsetId", config.toolsetId());
        payload.put("toolName", toolName);
        return payload;
    }

    public String activationReasonCode(String message) {
        String normalized = value(message, "").toUpperCase();
        if (normalized.contains("STALE")) return "MCP_POLICY_STALE";
        if (normalized.contains("PENDING_REVIEW") || normalized.contains("NOT_ACTIVE")) {
            return "MCP_POLICY_PENDING_REVIEW";
        }
        if (normalized.contains("REQUIRES_CHANGE_PACKAGE")) {
            return "MCP_TOOL_REQUIRES_CHANGE_PACKAGE";
        }
        return "MCP_TOOL_BLOCKED";
    }

    public String remoteReasonCode(String message) {
        String normalized = value(message, "");
        int delimiter = normalized.indexOf('：');
        if (delimiter > 0) return normalized.substring(0, delimiter);
        for (String code : List.of(
                "MCP_TOOL_ARGUMENT_POLICY_VIOLATION",
                "MCP_POLICY_PENDING_REVIEW",
                "MCP_POLICY_MISSING",
                "MCP_POLICY_STALE",
                "MCP_POLICY_DISABLED",
                "MCP_TOOL_REQUIRES_CHANGE_PACKAGE",
                "TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE",
                "MCP_STAGE_NOT_ALLOWED")) {
            if (normalized.contains(code)) return code;
        }
        return "MCP_TOOL_BLOCKED";
    }

    public String executionScope(McpExecutionRequest request) {
        return request.trustedLandingRuntime() && request.config().landingApproved()
                ? "APPROVED_LANDING" : "PRE_APPROVAL_WORKFLOW";
    }

    public String safeInputSummary(String value) {
        String input = value == null || value.isBlank() ? "{}" : value;
        return input.length() <= 2000 ? input : input.substring(0, 2000);
    }

    private boolean approvedReviewStatus(String reviewStatus) {
        String normalized = value(reviewStatus, "").toUpperCase(Locale.ROOT);
        return "HUMAN_REVIEWED".equals(normalized) || "SYSTEM_VERIFIED".equals(normalized);
    }

    private boolean matches(List<String> configured, String toolName) {
        if (configured == null || configured.isEmpty() || value(toolName, "").isBlank()) return false;
        String normalized = toolName.trim().toLowerCase(Locale.ROOT);
        return configured.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .anyMatch(item -> "*".equals(item) || normalized.equals(item));
    }

    private String normalizeEffectType(Object value) {
        String effectType = value(value, "UNKNOWN").toUpperCase();
        return "MUTATE_TEMP_RESOURCE".equals(effectType) ? "MUTATE_EPHEMERAL" : effectType;
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private String value(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
