package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderType;
import cn.lgs.orbisops.domain.toolset.model.ToolRiskLevel;
import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;

import java.util.List;

public final class JavaServiceLandingToolsetContributor implements OpsBuiltInToolsetContributor {

    public static final String TOOLSET_ID = "deployment.local-java";

    @Override
    public String contributorId() {
        return "java-service-landing";
    }

    @Override
    public int order() {
        return 675;
    }

    @Override
    public List<OpsToolsetDefinition> definitions() {
        ToolSemantics semantics = new ToolSemantics(false, false, true, true, true,
                ToolRiskLevel.HIGH, true, true);
        return List.of(OpsToolsetDefinition.builder()
                .toolsetId(TOOLSET_ID)
                .name("Java Artifact Landing")
                .description("Approved artifact delivery and previous-artifact recovery for configured Java execution resources")
                .tags(List.of("deployment", "landing", "java"))
                .sourceType("BUILT_IN")
                .adapterType(OpsLocalJavaServiceToolExecutionHandler.ADAPTER)
                .enabled(true)
                .readOnlyDefault(false)
                .tools(List.of(
                        tool(OpsLocalJavaServiceToolExecutionHandler.DEPLOY, "Deploy approved artifact", semantics),
                        tool(OpsLocalJavaServiceToolExecutionHandler.ROLLBACK, "Rollback previous artifact", semantics)))
                .build());
    }

    private OpsToolDefinition tool(String name, String displayName, ToolSemantics semantics) {
        ToolProviderDescriptor provider = new ToolProviderDescriptor(
                ToolProviderType.LOCAL,
                OpsLocalJavaServiceToolExecutionHandler.ADAPTER,
                OpsLocalJavaServiceToolExecutionHandler.ADAPTER,
                "",
                OpsLocalJavaServiceToolExecutionHandler.ADAPTER,
                name,
                "{}",
                "{}");
        return OpsToolDefinition.builder()
                .toolName(name)
                .displayName(displayName)
                .description(displayName)
                .parametersJson("{\"type\":\"object\"}")
                .outputSchemaJson("{\"type\":\"object\"}")
                .adapterType(OpsLocalJavaServiceToolExecutionHandler.ADAPTER)
                .readOnly(false)
                .writesRepairWorkspace(false)
                .writesTargetResource(true)
                .requiresChangePackage(true)
                .requiresApproval(true)
                .riskLevel(ToolRiskLevel.HIGH.name())
                .enabled(true)
                .providerDescriptor(provider)
                .semantics(semantics)
                .build();
    }
}
