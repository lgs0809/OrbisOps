package cn.lgs.orbisops.trigger.application.mcpexecution;

import cn.lgs.orbisops.application.mcpexecution.McpExecutionRouterPort;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionDecision;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionTarget;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolDefinition;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetDefinition;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsMcpExecutionRouterAdapter implements McpExecutionRouterPort {

    private final OpsToolsetRouter router;

    public OpsMcpExecutionRouterAdapter(OpsToolsetRouter router) {
        this.router = router;
    }

    @Override
    public McpExecutionDecision decide(McpExecutionRequest request, McpExecutionTarget target) {
        OpsToolDefinition tool = OpsToolDefinition.builder()
                .toolName(target.toolName())
                .displayName(target.toolName())
                .description("项目 MCP 远端工具：" + target.toolName())
                .adapterType("MCP")
                .mcpServerId(target.mcpId())
                .remoteToolName(target.toolName())
                .readOnly(target.readOnly())
                .writesRepairWorkspace(false)
                .writesTargetResource(target.writesTargetResource())
                .requiresChangePackage(target.requiresChangePackage())
                .requiresApproval(target.requiresApproval())
                .riskLevel(target.riskLevel())
                .enabled(target.enabled())
                .build();
        OpsToolsetDefinition toolset = OpsToolsetDefinition.builder()
                .toolsetId(target.toolsetId())
                .name(request.config().name())
                .description("Progressive MCP project adapter")
                .sourceType("PROJECT_MCP")
                .adapterType("MCP")
                .enabled(true)
                .readOnlyDefault(target.readOnly())
                .tools(List.of(tool))
                .build();
        OpsToolExecutionScope scope = request.trustedLandingRuntime()
                && request.config().landingApproved()
                ? OpsToolExecutionScope.APPROVED_LANDING
                : OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW;
        Map<String, Object> decision = router.decide(toolset, tool, scope, landingContext(request));
        return new McpExecutionDecision(
                Boolean.TRUE.equals(decision.get("allowed")),
                value(decision.get("decision"), "MCP_TOOL_BLOCKED"),
                value(decision.get("reasonCode"), value(decision.get("decision"), "MCP_TOOL_BLOCKED")),
                value(decision.get("message"), ""),
                Boolean.TRUE.equals(decision.get("retryable")),
                !Boolean.FALSE.equals(decision.get("terminal")),
                new LinkedHashMap<>(decision));
    }

    private Map<String, Object> landingContext(McpExecutionRequest request) {
        boolean trusted = request.trustedLandingRuntime() && request.config().landingApproved();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("landingApproved", trusted);
        context.put("changePackageId", request.config().changePackageId());
        context.put("approvedPackageHash", request.config().approvedPackageHash());
        context.put("approvedPackageVersion", request.config().approvedPackageVersion());
        context.put("operationId", request.config().operationId().isBlank()
                ? request.config().toolId() : request.config().operationId());
        context.put("internalCaller", trusted ? OpsToolsetRouter.LANDING_INTERNAL_CALLER : "");
        context.put("landingRuntimeToken", trusted ? OpsToolsetRouter.LANDING_RUNTIME_TOKEN : "");
        return context;
    }

    private String value(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
