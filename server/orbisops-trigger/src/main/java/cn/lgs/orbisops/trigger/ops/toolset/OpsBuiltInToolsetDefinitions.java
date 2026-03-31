package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

/** Package-local construction vocabulary shared by high-cohesion built-in contributors. */
final class OpsBuiltInToolsetDefinitions {

    private final OpsToolDefinitionFactory tools = new OpsToolDefinitionFactory();
    private final OpsToolsetDefinitionFactory toolsets = new OpsToolsetDefinitionFactory(tools);

    OpsToolDefinitionFactory tools() {
        return tools;
    }

    OpsToolsetDefinition toolset(
            String id,
            String name,
            String description,
            String adapterType,
            boolean readOnlyDefault,
            List<OpsToolDefinition> definitions) {
        return toolsets.toolset(
                id,
                name,
                description,
                adapterType,
                readOnlyDefault,
                definitions);
    }

    OpsToolsetDefinition targetWrite(String id, String name, String toolName) {
        return toolsets.targetWrite(id, name, toolName);
    }

}
