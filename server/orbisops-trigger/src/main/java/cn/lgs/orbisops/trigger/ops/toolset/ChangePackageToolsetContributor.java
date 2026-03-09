package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

public final class ChangePackageToolsetContributor implements OpsBuiltInToolsetContributor {

    private final OpsBuiltInToolsetDefinitions definitions = new OpsBuiltInToolsetDefinitions();

    @Override
    public String contributorId() {
        return "change-package";
    }

    @Override
    public int order() {
        return 700;
    }

    @Override
    public List<OpsToolsetDefinition> definitions() {
        OpsToolDefinitionFactory tools = definitions.tools();
        return List.of(definitions.toolset(
                "change_package",
                "ChangePackage",
                "创建、修订、提交审核和查看变更包",
                "CHANGE_PACKAGE",
                false,
                List.of(
                        tools.changePackage("change_package_create"),
                        tools.changePackage("change_package_revise"),
                        tools.read("change_package_detail", "查看变更包", "CHANGE_PACKAGE"),
                        tools.changePackage("change_package_submit_review"),
                        tools.changePackage("change_package_approve"),
                        tools.changePackage("change_package_reject"),
                        tools.read("change_package_list", "列出变更包", "CHANGE_PACKAGE"),
                        tools.changePackage("change_package_start_landing"),
                        tools.changePackage("change_package_landing_plan"))));
    }
}
