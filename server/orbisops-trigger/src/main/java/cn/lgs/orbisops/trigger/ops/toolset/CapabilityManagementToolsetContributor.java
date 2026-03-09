package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

/** Atomic governed mutations used by higher-level capability-management Skills. */
public final class CapabilityManagementToolsetContributor implements OpsBuiltInToolsetContributor {

    private final OpsBuiltInToolsetDefinitions definitions = new OpsBuiltInToolsetDefinitions();

    @Override
    public String contributorId() {
        return "capability-management";
    }

    @Override
    public int order() {
        return 875;
    }

    @Override
    public List<OpsToolsetDefinition> definitions() {
        OpsToolDefinitionFactory tools = definitions.tools();
        return List.of(definitions.toolset(
                "capability.manage",
                "Capability Management",
                "Skill/MCP 的原子持久化与导入动作；复杂创建、审查和接入流程应由对应 System Skill 驱动",
                "CAPABILITY_MANAGEMENT",
                false,
                List.of(
                        tools.capabilityManagement(
                                "skill_project_create",
                                "把已经完成设计和校验的 Skill 作为项目 Skill 创建；默认保持受治理状态"),
                        tools.capabilityManagement(
                                "skill_package_import",
                                "把已经完成来源审查的外部 Skill 包导入当前项目；导入后保持 PAUSED 待复核"),
                        tools.capabilityManagement(
                                "mcp_server_import",
                                "把已经完成来源与治理信息检查的 MCP Server 接入当前项目"))));
    }
}
