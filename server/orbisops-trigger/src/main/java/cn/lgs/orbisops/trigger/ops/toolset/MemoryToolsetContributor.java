package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

/** Explicit Memory operations available to the foreground Agent as normal capabilities. */
public final class MemoryToolsetContributor implements OpsBuiltInToolsetContributor {

    private final OpsBuiltInToolsetDefinitions definitions = new OpsBuiltInToolsetDefinitions();

    @Override
    public String contributorId() {
        return "memory";
    }

    @Override
    public int order() {
        return 650;
    }

    @Override
    public List<OpsToolsetDefinition> definitions() {
        OpsToolDefinitionFactory tools = definitions.tools();
        return List.of(definitions.toolset(
                "memory",
                "Memory",
                "显式保存和查询长期 Memory；仅在用户明确要求记住或主动查询 Memory 时调用",
                "MEMORY",
                false,
                List.of(
                        tools.memory("memory_upsert", "持久化用户明确要求保存的偏好、项目事实或工作流约束"),
                        tools.memory("memory_search", "查询当前用户、项目和会话可见的长期 Memory"))));
    }
}
