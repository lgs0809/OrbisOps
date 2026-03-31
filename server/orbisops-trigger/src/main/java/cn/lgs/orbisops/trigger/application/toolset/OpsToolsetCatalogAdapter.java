package cn.lgs.orbisops.trigger.application.toolset;

import cn.lgs.orbisops.application.toolset.ToolsetCatalogPort;
import cn.lgs.orbisops.application.toolset.ToolsetRefreshOutcome;
import cn.lgs.orbisops.domain.toolset.model.ToolDefinition;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderType;
import cn.lgs.orbisops.domain.toolset.model.ToolRiskLevel;
import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;
import cn.lgs.orbisops.domain.toolset.model.ToolsetDefinition;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolDefinition;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetCatalogService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolsetDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Trigger anti-corruption adapter from legacy Toolset DTOs to Domain aggregates. */
@Component
public class OpsToolsetCatalogAdapter implements ToolsetCatalogPort {

    private final OpsToolsetCatalogService service;

    public OpsToolsetCatalogAdapter(OpsToolsetCatalogService service) {
        if (service == null) throw new IllegalArgumentException("TOOLSET_CATALOG_SERVICE_REQUIRED");
        this.service = service;
    }

    @Override
    public List<ToolsetDefinition> listBuiltIn() {
        return service.listBuiltInToolsets().stream().map(this::definition).toList();
    }

    @Override
    public List<ToolsetDefinition> listCustom(String projectId) {
        return service.listCustomToolsets(projectId).stream().map(this::definition).toList();
    }

    @Override
    public List<ToolsetDefinition> listEffective(String projectId, String userId) {
        return service.listEffectiveToolsets(projectId, userId).stream()
                .map(this::definition)
                .toList();
    }

    @Override
    public ToolsetDefinition registerCustom(
            String projectId,
            Map<String, Object> normalized,
            String actor) {
        return definition(service.registerCustomToolset(projectId, normalized, actor));
    }

    @Override
    public ToolsetDefinition setEnabled(
            String projectId,
            String toolsetId,
            boolean enabled,
            String actor) {
        if (enabled) service.enableToolset(projectId, toolsetId, actor);
        else service.disableToolset(projectId, toolsetId, actor);
        return service.listCustomToolsets(projectId).stream()
                .filter(item -> toolsetId.equals(text(item.getToolsetId())))
                .findFirst()
                .map(this::definition)
                .orElseThrow(() -> new IllegalStateException(
                        "TOOLSET_STATE_NOT_VISIBLE:" + toolsetId));
    }

    @Override
    public ToolsetRefreshOutcome refreshMcp(
            String projectId,
            String toolsetId,
            String actor) {
        return service.refreshMcpToolset(projectId, toolsetId, actor);
    }

    private ToolsetDefinition definition(OpsToolsetDefinition source) {
        if (source == null) throw new IllegalArgumentException("TOOLSET_DEFINITION_REQUIRED");
        return new ToolsetDefinition(
                source.getToolsetId(),
                source.getName(),
                source.getDescription(),
                source.getPrerequisites(),
                source.getTags(),
                fallback(source.getSourceType(), "CUSTOM"),
                fallback(source.getAdapterType(), "UNKNOWN"),
                source.isEnabled(),
                source.isReadOnlyDefault(),
                source.getTools() == null
                        ? List.of()
                        : source.getTools().stream().map(this::tool).toList(),
                source.getCreateBy(),
                source.getUpdateBy(),
                source.getCreateTime(),
                source.getUpdateTime());
    }

    private ToolDefinition tool(OpsToolDefinition source) {
        return new ToolDefinition(
                source.getToolName(),
                source.getDisplayName(),
                source.getDescription(),
                source.getParametersJson(),
                fallback(source.getAdapterType(), "UNKNOWN"),
                source.getCommandTemplate(),
                source.getMcpServerId(),
                source.getRemoteToolName(),
                source.getHttpConfigJson(),
                source.getDbConfigJson(),
                source.isReadOnly(),
                source.isWritesRepairWorkspace(),
                source.isWritesTargetResource(),
                source.isRequiresChangePackage(),
                source.isRequiresApproval(),
                ToolRiskLevel.require(source.getRiskLevel(), ToolRiskLevel.HIGH),
                source.getOutputBudgetJson(),
                source.isEnabled(),
                provider(source),
                semantics(source),
                new cn.lgs.orbisops.domain.toolset.model.ToolSchema(
                        source.getParametersJson(),
                        source.getOutputSchemaJson()),
                source.getGovernance());
    }

    private ToolProviderDescriptor provider(OpsToolDefinition source) {
        if (source.getProviderDescriptor() != null) return source.getProviderDescriptor();
        String adapterType = fallback(source.getAdapterType(), "UNKNOWN");
        ToolProviderType providerType = adapterType.startsWith("LOCAL_")
                ? ToolProviderType.LOCAL
                : "MCP".equalsIgnoreCase(adapterType)
                        ? ToolProviderType.MCP
                        : ToolProviderType.BUILT_IN;
        return new ToolProviderDescriptor(
                providerType,
                adapterType,
                adapterType,
                source.getCommandTemplate(),
                source.getMcpServerId(),
                source.getRemoteToolName(),
                source.getHttpConfigJson(),
                source.getDbConfigJson());
    }

    private ToolSemantics semantics(OpsToolDefinition source) {
        if (source.getSemantics() != null) return source.getSemantics();
        boolean idempotent = source.isReadOnly();
        return new ToolSemantics(
                source.isReadOnly(),
                source.isWritesRepairWorkspace(),
                source.isWritesTargetResource(),
                source.isRequiresChangePackage(),
                source.isRequiresApproval(),
                ToolRiskLevel.require(source.getRiskLevel(), ToolRiskLevel.HIGH),
                idempotent,
                idempotent);
    }

    private String fallback(String value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
