package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderType;
import cn.lgs.orbisops.domain.toolset.model.ToolRiskLevel;
import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;
import cn.lgs.orbisops.domain.toolset.model.business.UpdateAlertThresholdPolicy;

import java.util.List;

/** Exactly one production write capability; provider identity is bound per project at invocation time. */
public final class BusinessOperationsToolsetContributor implements OpsBuiltInToolsetContributor {

    @Override
    public String contributorId() {
        return "business-operations";
    }

    @Override
    public int order() {
        return 650;
    }

    @Override
    public List<OpsToolsetDefinition> definitions() {
        ToolSemantics semantics = new ToolSemantics(
                false,
                false,
                true,
                true,
                true,
                ToolRiskLevel.HIGH,
                true,
                true);
        ToolProviderDescriptor provider = new ToolProviderDescriptor(
                ToolProviderType.MCP,
                "MCP",
                "MCP",
                "",
                "MCP",
                UpdateAlertThresholdPolicy.TOOL_NAME,
                "{}",
                "{}");
        OpsToolDefinition update = OpsToolDefinition.builder()
                .toolName(UpdateAlertThresholdPolicy.TOOL_NAME)
                .displayName("Update Alert Threshold")
                .description("Update one alert threshold with expected-state/version CAS and an authoritative receipt")
                .parametersJson(UpdateAlertThresholdPolicy.INPUT_SCHEMA)
                .outputSchemaJson(UpdateAlertThresholdPolicy.OUTPUT_SCHEMA)
                .adapterType("MCP")
                .mcpServerId("MCP")
                .remoteToolName(UpdateAlertThresholdPolicy.TOOL_NAME)
                .readOnly(false)
                .writesRepairWorkspace(false)
                .writesTargetResource(true)
                .requiresChangePackage(true)
                .requiresApproval(true)
                .riskLevel(ToolRiskLevel.HIGH.name())
                .enabled(true)
                .providerDescriptor(provider)
                .semantics(semantics)
                .governance(UpdateAlertThresholdPolicy.governance())
                .build();
        return List.of(OpsToolsetDefinition.builder()
                .toolsetId(UpdateAlertThresholdPolicy.TOOLSET_ID)
                .name("Alert Threshold Operations")
                .description("Approved, idempotent and reconcilable business threshold operation")
                .tags(List.of("ops", "business-write", "platform-trusted"))
                .sourceType("BUILT_IN")
                .adapterType("MCP")
                .enabled(true)
                .readOnlyDefault(false)
                .tools(List.of(update))
                .build());
    }
}
