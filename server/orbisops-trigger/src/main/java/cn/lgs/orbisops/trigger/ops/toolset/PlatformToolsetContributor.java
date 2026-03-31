package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

public final class PlatformToolsetContributor implements OpsBuiltInToolsetContributor {

    private final OpsBuiltInToolsetDefinitions definitions = new OpsBuiltInToolsetDefinitions();

    @Override
    public String contributorId() {
        return "platform";
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    public List<OpsToolsetDefinition> definitions() {
        OpsToolDefinitionFactory tools = definitions.tools();
        return List.of(
                definitions.toolset(
                        "infra.k8s.readonly",
                        "Kubernetes 只读",
                        "查询 K8s 对象和事件",
                        "MCP",
                        true,
                        List.of(tools.read("k8s_get", "查询 Kubernetes 资源"))),
                definitions.targetWrite(
                        "infra.k8s.remediation",
                        "Kubernetes 修复",
                        "k8s_apply"),
                definitions.toolset(
                        "cicd.jenkins.readonly",
                        "Jenkins 只读",
                        "查询构建、流水线和制品状态",
                        "MCP",
                        true,
                        List.of(tools.read("jenkins_build_status", "查询构建状态"))),
                definitions.targetWrite(
                        "cicd.deploy",
                        "部署执行",
                        "jenkins_deploy"),
                definitions.toolset(
                        "config.nacos.readonly",
                        "Nacos 只读",
                        "查询配置和历史版本",
                        "MCP",
                        true,
                        List.of(tools.read("nacos_get_config", "读取 Nacos 配置"))),
                definitions.targetWrite(
                        "config.nacos.publish",
                        "Nacos 发布",
                        "nacos_publish"),
                definitions.toolset(
                        "deployment.records",
                        "发布记录",
                        "查询服务发布和版本记录",
                        "HTTP_API",
                        true,
                        List.of(tools.read("deployment_records_query", "查询发布记录"))),
                definitions.targetWrite(
                        "release.platform.execute",
                        "发布平台执行",
                        "release_execute"),
                definitions.targetWrite(
                        "job.platform.execute",
                        "作业平台执行",
                        "job_execute"));
    }
}
