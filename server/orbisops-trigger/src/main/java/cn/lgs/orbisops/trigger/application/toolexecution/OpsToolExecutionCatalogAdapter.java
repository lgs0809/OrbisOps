package cn.lgs.orbisops.trigger.application.toolexecution;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionCatalogPort;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionDecision;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionResolution;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderType;
import cn.lgs.orbisops.domain.toolset.model.ToolSchema;
import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolDefinition;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetCatalogService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetDefinition;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetRouter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsToolExecutionCatalogAdapter implements ToolExecutionCatalogPort {

    private final OpsToolsetCatalogService catalog;
    private final OpsToolsetRouter router;

    public OpsToolExecutionCatalogAdapter(
            OpsToolsetCatalogService catalog,
            OpsToolsetRouter router) {
        this.catalog = catalog;
        this.router = router;
    }

    @Override
    public ToolExecutionResolution resolve(ToolExecutionRequest request) {
        OpsMcpToolExecutionBinding mcpBinding = mcpBinding(request);
        List<OpsToolsetDefinition> toolsets = catalog.listEffectiveToolsets(
                request.projectId(), request.userId());
        OpsToolsetDefinition toolset = toolsets.stream()
                .filter(item -> request.toolsetId().equals(item.getToolsetId()))
                .findFirst()
                .orElse(null);
        if (toolset != null) {
            OpsToolDefinition tool = toolset.getTools().stream()
                    .filter(item -> request.toolName().equals(item.getToolName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "工具不可用：" + request.toolsetId() + "/" + request.toolName()));
            return resolution(
                    request,
                    toolset,
                    mcpBinding == null ? tool : bindTrustedMcpTool(tool, mcpBinding));
        }
        if (mcpBinding != null) {
            return resolveMcpCompatibility(request, mcpBinding);
        }
        throw new IllegalArgumentException("工具集不可用：" + request.toolsetId());
    }

    private ToolExecutionResolution resolveMcpCompatibility(
            ToolExecutionRequest request,
            OpsMcpToolExecutionBinding binding) {
        ToolSemantics semantics = Boolean.TRUE.equals(binding.readOnlyHint())
                ? ToolSemantics.readOnlyTool()
                : request.scope().name().equals("APPROVED_LANDING")
                        ? ToolSemantics.targetResourceWrite()
                        : ToolSemantics.validation();
        ToolProviderDescriptor provider = new ToolProviderDescriptor(
                ToolProviderType.MCP,
                binding.mcpId(),
                "MCP",
                "",
                binding.mcpId(),
                binding.remoteToolName(),
                "{}",
                "{}");
        OpsToolDefinition tool = OpsToolDefinition.builder()
                .toolName(binding.remoteToolName())
                .displayName(binding.remoteToolName())
                .description("Legacy MCP compatibility execution")
                .adapterType("MCP")
                .mcpServerId(binding.mcpId())
                .remoteToolName(binding.remoteToolName())
                .parametersJson("{}")
                .readOnly(semantics.readOnly())
                .writesRepairWorkspace(semantics.writesRepairWorkspace())
                .writesTargetResource(semantics.writesTargetResource())
                .requiresChangePackage(semantics.requiresChangePackage())
                .requiresApproval(semantics.requiresApproval())
                .riskLevel(semantics.riskLevel().name())
                .providerDescriptor(provider)
                .semantics(semantics)
                .enabled(true)
                .build();
        OpsToolsetDefinition toolset = OpsToolsetDefinition.builder()
                .toolsetId("mcp." + binding.mcpId())
                .name(binding.mcpId())
                .description("Legacy MCP compatibility toolset")
                .sourceType("MCP_COMPATIBILITY")
                .adapterType("MCP")
                .enabled(true)
                .readOnlyDefault(semantics.readOnly())
                .tools(List.of(tool))
                .build();
        return resolution(request, toolset, tool);
    }

    private OpsToolDefinition bindTrustedMcpTool(
            OpsToolDefinition trusted,
            OpsMcpToolExecutionBinding binding) {
        ToolProviderDescriptor existing = trusted.getProviderDescriptor();
        ToolProviderDescriptor provider = new ToolProviderDescriptor(
                ToolProviderType.MCP,
                binding.mcpId(),
                "MCP",
                "",
                binding.mcpId(),
                binding.remoteToolName(),
                existing == null ? "{}" : existing.httpConfigJson(),
                existing == null ? "{}" : existing.dbConfigJson());
        return OpsToolDefinition.builder()
                .toolName(trusted.getToolName())
                .displayName(trusted.getDisplayName())
                .description(trusted.getDescription())
                .parametersJson(trusted.getParametersJson())
                .outputSchemaJson(trusted.getOutputSchemaJson())
                .adapterType("MCP")
                .commandTemplate(trusted.getCommandTemplate())
                .mcpServerId(binding.mcpId())
                .remoteToolName(binding.remoteToolName())
                .httpConfigJson(trusted.getHttpConfigJson())
                .dbConfigJson(trusted.getDbConfigJson())
                .readOnly(trusted.isReadOnly())
                .writesRepairWorkspace(trusted.isWritesRepairWorkspace())
                .writesTargetResource(trusted.isWritesTargetResource())
                .requiresChangePackage(trusted.isRequiresChangePackage())
                .requiresApproval(trusted.isRequiresApproval())
                .riskLevel(trusted.getRiskLevel())
                .outputBudgetJson(trusted.getOutputBudgetJson())
                .enabled(trusted.isEnabled())
                .providerDescriptor(provider)
                .semantics(trusted.getSemantics())
                .governance(trusted.getGovernance())
                .build();
    }

    private ToolExecutionResolution resolution(
            ToolExecutionRequest request,
            OpsToolsetDefinition toolset,
            OpsToolDefinition tool) {
        Map<String, Object> decision = router.decide(
                toolset,
                tool,
                OpsToolExecutionScope.valueOf(request.scope().name()),
                request.landingContext());
        ToolExecutionTarget target = new ToolExecutionTarget(
                toolset.getToolsetId(),
                tool.getToolName(),
                adapterType(toolset, tool),
                value(decision.get("riskLevel"), tool.getRiskLevel()),
                tool.isReadOnly(),
                tool.isWritesRepairWorkspace(),
                tool.isWritesTargetResource(),
                tool.isRequiresChangePackage(),
                tool.isRequiresApproval(),
                tool.getProviderDescriptor(),
                tool.getSemantics(),
                new ToolSchema(tool.getParametersJson(), tool.getOutputSchemaJson()),
                tool.getGovernance());
        ToolExecutionDecision typedDecision = new ToolExecutionDecision(
                Boolean.TRUE.equals(decision.get("allowed")),
                value(decision.get("decision"), "TOOL_NOT_AVAILABLE"),
                value(decision.get("reasonCode"),
                        value(decision.get("decision"), "TOOL_NOT_AVAILABLE")),
                value(decision.get("message"), ""),
                value(decision.get("riskLevel"), target.riskLevel()),
                new LinkedHashMap<>(decision));
        return new ToolExecutionResolution(target, typedDecision);
    }

    private OpsMcpToolExecutionBinding mcpBinding(ToolExecutionRequest request) {
        if (request == null) return null;
        Object value = request.requestContext().get(OpsMcpToolExecutionBinding.CONTEXT_KEY);
        return value instanceof OpsMcpToolExecutionBinding binding ? binding : null;
    }

    private String adapterType(OpsToolsetDefinition toolset, OpsToolDefinition tool) {
        String toolAdapter = value(tool.getAdapterType(), "");
        return toolAdapter.isBlank() ? value(toolset.getAdapterType(), "UNKNOWN") : toolAdapter;
    }

    private String value(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? (fallback == null ? "" : fallback.trim()) : normalized;
    }
}
