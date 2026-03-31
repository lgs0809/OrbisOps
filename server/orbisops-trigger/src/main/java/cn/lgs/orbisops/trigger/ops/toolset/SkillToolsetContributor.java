package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

public final class SkillToolsetContributor implements OpsBuiltInToolsetContributor {

    private final OpsBuiltInToolsetDefinitions definitions = new OpsBuiltInToolsetDefinitions();

    @Override
    public String contributorId() {
        return "skill";
    }

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public List<OpsToolsetDefinition> definitions() {
        OpsToolDefinitionFactory tools = definitions.tools();
        return List.of(definitions.toolset(
                "skill.catalog",
                "Skill 目录",
                "检索本次 Work Session 已固定的完整 Skill 文件包并按需读取入口或资源文件",
                "SKILL",
                true,
                List.of(
                        tools.read("skill_search", "在当前 Work Session 的轻量 Skill 目录中检索候选", "SKILL"),
                        tools.read("skill_load", "读取当前 Work Session 已固定的 Skill 入口、文件清单或指定文本资源", "SKILL"))));
    }
}
