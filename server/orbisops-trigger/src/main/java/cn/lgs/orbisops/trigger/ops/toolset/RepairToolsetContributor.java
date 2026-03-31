package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

public final class RepairToolsetContributor implements OpsBuiltInToolsetContributor {

    private final OpsBuiltInToolsetDefinitions definitions = new OpsBuiltInToolsetDefinitions();

    @Override
    public String contributorId() {
        return "repair";
    }

    @Override
    public int order() {
        return 600;
    }

    @Override
    public List<OpsToolsetDefinition> definitions() {
        OpsToolDefinitionFactory tools = definitions.tools();
        return List.of(
                definitions.toolset(
                        "code.repository",
                        "代码仓库读取",
                        "读取、搜索和枚举已登记代码仓库",
                        "CODE_REPAIR",
                        true,
                        List.of(
                                tools.read("code_read", "读取文件", "CODE_REPAIR"),
                                tools.read("code_grep", "搜索代码", "CODE_REPAIR"),
                                tools.read("code_glob", "按模式查找文件", "CODE_REPAIR"))),
                definitions.toolset(
                        "code.repair",
                        "受控代码修复",
                        "在隔离 repair worktree 内修改、测试和提交候选修复",
                        "CODE_REPAIR",
                        false,
                        List.of(
                                tools.repair("ValidateCodeCandidate"),
                                tools.repair("code_enter_worktree"),
                                tools.repair("code_edit"),
                                tools.repair("code_write"),
                                tools.repair("code_bash"),
                                tools.repair("code_compute_diff"),
                                tools.repair("code_commit_repair"),
                                tools.repair("code_exit_worktree"),
                                tools.repair("code_lsp"))));
    }
}
