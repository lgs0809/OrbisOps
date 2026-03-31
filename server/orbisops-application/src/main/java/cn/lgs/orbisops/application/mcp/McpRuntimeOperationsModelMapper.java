package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.domain.mcp.model.McpToolRuntimeAccess;
import cn.lgs.orbisops.domain.mcp.service.McpToolPolicyGovernance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class McpRuntimeOperationsModelMapper {

    private final McpJsonCodec jsonCodec;
    private final McpRuntimePayloadSanitizerPort payloadSanitizer;
    private final McpToolPolicyGovernance governance;

    McpRuntimeOperationsModelMapper(McpJsonCodec jsonCodec,
                                    McpRuntimePayloadSanitizerPort payloadSanitizer,
                                    McpToolPolicyGovernance governance) {
        if (jsonCodec == null) throw new IllegalArgumentException("MCP_JSON_CODEC_REQUIRED");
        if (payloadSanitizer == null) throw new IllegalArgumentException("MCP_RUNTIME_PAYLOAD_SANITIZER_REQUIRED");
        if (governance == null) throw new IllegalArgumentException("MCP_POLICY_GOVERNANCE_REQUIRED");
        this.jsonCodec = jsonCodec;
        this.payloadSanitizer = payloadSanitizer;
        this.governance = governance;
    }

    boolean isPreApprovalExecutable(McpToolPolicyProjection policy) {
        if (policy == null) return false;
        return governance.isPreApprovalExecutable(new McpToolRuntimeAccess(
                policy.effectType(),
                policy.effectScope(),
                policy.mutability(),
                policy.riskLevel(),
                policy.readOnly(),
                policy.investigateAllowed(),
                policy.prepareAllowed(),
                policy.requiresApprovedPackage()));
    }

    Map<String, Object> toolMeta(List<Map<String, Object>> tools, String toolId) {
        String normalized = text(toolId);
        if (normalized.isBlank() || tools == null || tools.isEmpty()) return Map.of();
        return tools.stream()
                .filter(tool -> normalized.equals(text(tool.get("toolId")))
                        || normalized.equals(text(tool.get("mcpId"))))
                .findFirst()
                .map(this::copy)
                .orElse(Map.of());
    }

    String sanitize(Object value) {
        return payloadSanitizer.sanitize(value);
    }

    String encode(Object value) {
        return jsonCodec.encode(value);
    }

    Map<String, Object> copy(Map<String, Object> source) {
        return source == null || source.isEmpty() ? Map.of() : new LinkedHashMap<>(source);
    }

    boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value);
        if (normalized.isBlank()) return fallback;
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "y".equalsIgnoreCase(normalized);
    }

    String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    String text(Object value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    String risk(Object value, String fallback) {
        return text(value, fallback).toUpperCase(Locale.ROOT);
    }
}
