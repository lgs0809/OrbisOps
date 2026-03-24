package cn.lgs.orbisops.trigger.ops.capability;

import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.LinkedHashMap;
import java.util.Map;

/** MCP import, audit, and public result projection boundary. */
final class OpsMcpCapabilityImportCoordinator {

    private final OpsMcpCapabilityImporter importer;
    private final OpsConfigAuditService auditService;

    OpsMcpCapabilityImportCoordinator(
            OpsMcpCapabilityImporter importer,
            OpsConfigAuditService auditService) {
        this.importer = importer;
        this.auditService = auditService;
    }

    Map<String, Object> importCapability(
            String projectId,
            String sourceUrl,
            Map<String, Object> slots,
            String actor) {
        OpsMcpCapabilityImporter.Result imported = importer.importCapability(
                new OpsMcpCapabilityImporter.Input(
                        projectId,
                        sourceUrl,
                        OpsCapabilityImportValues.text(slots.get("capabilityName")),
                        OpsCapabilityImportValues.text(slots.get("credentialRef")),
                        OpsCapabilityImportValues.text(slots.get("transportType"))));
        auditService.recordRuntimeEvent(
                projectId,
                "",
                actor,
                "capability-import",
                "MCP_REGISTERED",
                imported.mcpId(),
                "HIGH",
                "DISCOVERY_FAILED".equals(imported.discoveryStatus())
                        ? "FAILED"
                        : "SUCCEEDED",
                Map.of(
                        "projectId", projectId,
                        "mcpId", imported.mcpId(),
                        "endpoint", imported.endpoint(),
                        "credentialRef", imported.credentialRef(),
                        "status", "PENDING_REVIEW",
                        "discoveryStatus", imported.discoveryStatus(),
                        "discoveryError", imported.discoveryError()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capabilityType", "MCP");
        result.put("status", imported.discoveryStatus());
        result.put(
                "message",
                "DISCOVERY_FAILED".equals(imported.discoveryStatus())
                        ? "MCP Server 已登记，但真实工具发现失败。修复连通性或凭据后重试；当前不会向 Agent 暴露。"
                        : "MCP 工具已真实发现并生成平台策略建议。管理员完成人工审核前，不会向 Agent 暴露。");
        result.put("mcp", imported.mcp());
        result.put("discoveredTools", imported.discoveredTools());
        result.put("policySuggestions", imported.policySuggestions());
        result.put("discoveryError", imported.discoveryError());
        return result;
    }
}
