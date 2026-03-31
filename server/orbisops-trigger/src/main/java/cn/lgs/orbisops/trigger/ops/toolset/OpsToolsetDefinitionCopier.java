package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.ArrayList;

/** Deep-copies mutable toolset DTOs at registry boundaries. */
public final class OpsToolsetDefinitionCopier {

    public OpsToolsetDefinition copy(OpsToolsetDefinition source) {
        if (source == null) throw new IllegalArgumentException("TOOLSET_DEFINITION_REQUIRED");
        return OpsToolsetDefinition.builder()
                .toolsetId(source.getToolsetId())
                .name(source.getName())
                .description(source.getDescription())
                .prerequisites(source.getPrerequisites())
                .tags(source.getTags() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(source.getTags()))
                .sourceType(source.getSourceType())
                .adapterType(source.getAdapterType())
                .enabled(source.isEnabled())
                .readOnlyDefault(source.isReadOnlyDefault())
                .tools(source.getTools() == null
                        ? new ArrayList<>()
                        : source.getTools().stream().map(this::copyTool).toList())
                .createBy(source.getCreateBy())
                .updateBy(source.getUpdateBy())
                .createTime(source.getCreateTime())
                .updateTime(source.getUpdateTime())
                .build();
    }

    public OpsToolDefinition copyTool(OpsToolDefinition source) {
        if (source == null) throw new IllegalArgumentException("TOOL_DEFINITION_REQUIRED");
        return OpsToolDefinition.builder()
                .toolName(source.getToolName())
                .displayName(source.getDisplayName())
                .description(source.getDescription())
                .parametersJson(source.getParametersJson())
                .outputSchemaJson(source.getOutputSchemaJson())
                .adapterType(source.getAdapterType())
                .commandTemplate(source.getCommandTemplate())
                .mcpServerId(source.getMcpServerId())
                .remoteToolName(source.getRemoteToolName())
                .httpConfigJson(source.getHttpConfigJson())
                .dbConfigJson(source.getDbConfigJson())
                .readOnly(source.isReadOnly())
                .writesRepairWorkspace(source.isWritesRepairWorkspace())
                .writesTargetResource(source.isWritesTargetResource())
                .requiresChangePackage(source.isRequiresChangePackage())
                .requiresApproval(source.isRequiresApproval())
                .riskLevel(source.getRiskLevel())
                .outputBudgetJson(source.getOutputBudgetJson())
                .enabled(source.isEnabled())
                .providerDescriptor(source.getProviderDescriptor())
                .semantics(source.getSemantics())
                .governance(source.getGovernance())
                .build();
    }
}
