package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderType;
import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;

/** Creates tool definitions from stable provider, execution and risk semantics. */
public final class OpsToolDefinitionFactory {

    public OpsToolDefinition read(String name, String description) {
        return read(name, description, "MCP");
    }

    public OpsToolDefinition read(String name, String description, String adapterType) {
        return definition(name, name, description,
                provider(adapterType), ToolSemantics.readOnlyTool());
    }

    public OpsToolDefinition repair(String name) {
        return definition(name, name,
                "受控 repair worktree 工具：" + name,
                provider("CODE_REPAIR"),
                ToolSemantics.repairWorkspaceWrite());
    }

    public OpsToolDefinition validation(String name, String description) {
        return definition(name, name, description,
                provider("LOCAL_VALIDATION"),
                ToolSemantics.validation());
    }

    public OpsToolDefinition changePackage(String name) {
        return definition(name, name,
                "ChangePackage 工具：" + name,
                provider("CHANGE_PACKAGE"),
                ToolSemantics.changePackageCommand());
    }

    public OpsToolDefinition memory(String name, String description) {
        return definition(name, name, description,
                provider("MEMORY"),
                ToolSemantics.workflowCommand());
    }

    public OpsToolDefinition capabilityManagement(String name, String description) {
        return definition(name, name, description,
                provider("CAPABILITY_MANAGEMENT"),
                ToolSemantics.configurationWrite());
    }

    public OpsToolDefinition inspection(String name, String description) {
        return workflow(name, description, "INSPECTION_TASK");
    }

    public OpsToolDefinition alertTrigger(String name, String description) {
        return workflow(name, description, "ALERT_TRIGGER");
    }

    public OpsToolDefinition channel(String name, String description) {
        return workflow(name, description, "CHANNEL");
    }

    public OpsToolDefinition targetWrite(String toolName, String displayName) {
        return definition(toolName, displayName, displayName,
                provider("MCP"), ToolSemantics.targetResourceWrite());
    }

    private OpsToolDefinition workflow(
            String name,
            String description,
            String adapterType) {
        return definition(name, name, description,
                provider(adapterType), ToolSemantics.workflowCommand());
    }

    private OpsToolDefinition definition(
            String toolName,
            String displayName,
            String description,
            ToolProviderDescriptor provider,
            ToolSemantics semantics) {
        return OpsToolDefinition.builder()
                .toolName(toolName)
                .displayName(displayName)
                .description(description)
                .adapterType(provider.adapterType())
                .commandTemplate(provider.commandTemplate())
                .mcpServerId(provider.mcpServerId())
                .remoteToolName(provider.remoteToolName())
                .httpConfigJson(provider.httpConfigJson())
                .dbConfigJson(provider.dbConfigJson())
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
    }

    private ToolProviderDescriptor provider(String adapterType) {
        ToolProviderType type;
        if (adapterType.startsWith("LOCAL_")) {
            type = ToolProviderType.LOCAL;
        } else if ("MCP".equals(adapterType)) {
            type = ToolProviderType.MCP;
        } else if ("HTTP_API".equals(adapterType)) {
            type = ToolProviderType.HTTP_API;
        } else if ("SKILL".equals(adapterType)) {
            type = ToolProviderType.SKILL;
        } else {
            type = ToolProviderType.BUILT_IN;
        }
        return new ToolProviderDescriptor(
                type,
                adapterType,
                adapterType,
                "",
                "",
                "",
                "{}",
                "{}");
    }
}
