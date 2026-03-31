package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;
import java.util.Locale;

/** Creates toolset definitions and target-write variants. */
public final class OpsToolsetDefinitionFactory {

    private final OpsToolDefinitionFactory tools;

    public OpsToolsetDefinitionFactory(OpsToolDefinitionFactory tools) {
        if (tools == null) throw new IllegalArgumentException("TOOL_DEFINITION_FACTORY_REQUIRED");
        this.tools = tools;
    }

    public OpsToolsetDefinition toolset(
            String id,
            String name,
            String description,
            String adapterType,
            boolean readOnlyDefault,
            List<OpsToolDefinition> definitions) {
        return OpsToolsetDefinition.builder()
                .toolsetId(id)
                .name(name)
                .description(description)
                .sourceType("BUILT_IN")
                .adapterType(adapterType)
                .enabled(true)
                .readOnlyDefault(readOnlyDefault)
                .tags(tagsFor(id))
                .tools(definitions)
                .build();
    }

    public OpsToolsetDefinition targetWrite(
            String id,
            String name,
            String toolName) {
        return toolset(
                id,
                name,
                "生产目标资源写工具，只能由 approved ChangePackage 的 LandingRuntime 调用",
                "MCP",
                false,
                List.of(tools.targetWrite(toolName, name)));
    }

    private List<String> tagsFor(String id) {
        return List.of(id.split("\\.")[0].toLowerCase(Locale.ROOT));
    }
}
