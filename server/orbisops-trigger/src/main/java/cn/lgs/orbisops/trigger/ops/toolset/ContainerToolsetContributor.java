package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

public final class ContainerToolsetContributor implements OpsBuiltInToolsetContributor {

    private final OpsBuiltInToolsetDefinitions definitions = new OpsBuiltInToolsetDefinitions();

    @Override
    public String contributorId() {
        return "container";
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public List<OpsToolsetDefinition> definitions() {
        OpsToolDefinitionFactory tools = definitions.tools();
        return List.of(
                definitions.toolset(
                        "container.docker.readonly",
                        "Docker 只读与验证",
                        "查询容器状态、日志和 compose 配置",
                        "LOCAL_DOCKER",
                        true,
                        List.of(
                                tools.read("docker_ps", "列出容器", "LOCAL_DOCKER"),
                                tools.read("docker_inspect", "检查容器", "LOCAL_DOCKER"),
                                tools.read("docker_logs", "读取容器日志", "LOCAL_DOCKER"),
                                tools.read("docker_compose_config", "读取 compose 配置", "LOCAL_DOCKER"),
                                tools.validation("docker_compose_config_validate", "验证 compose 配置"),
                                tools.validation("docker_restart_plan_dry_run", "重启计划 dry-run"),
                                tools.read("docker_image_digest_check", "检查镜像 digest", "LOCAL_DOCKER"))));
    }
}
