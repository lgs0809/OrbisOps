package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.LocalHostApplicationService;

import java.util.List;
import java.util.Map;

/** Docker read/dry-run local tool protocol adapter. */
public final class OpsLocalDockerAdapter {

    private final LocalHostApplicationService service;
    private final OpsLocalAdapterSettings settings;

    public OpsLocalDockerAdapter(
            LocalHostApplicationService service,
            OpsLocalAdapterSettings settings) {
        if (service == null) throw new IllegalArgumentException("LOCAL_HOST_SERVICE_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("LOCAL_ADAPTER_SETTINGS_REQUIRED");
        this.service = service;
        this.settings = settings;
    }

    public Map<String, Object> execute(
            String toolName,
            OpsLocalToolArguments args) {
        List<String> command = switch (toolName) {
            case "docker_ps" -> List.of("docker", "ps", "--format", "json");
            case "docker_inspect" -> List.of(
                    "docker",
                    "inspect",
                    service.dockerName(args.raw("container")));
            case "docker_logs" -> List.of(
                    "docker",
                    "logs",
                    "--tail",
                    String.valueOf(args.boundedInt("tail", 1, 500, 100)),
                    service.dockerName(args.raw("container")));
            case "docker_compose_config", "docker_compose_config_validate" ->
                    List.of("docker", "compose", "config");
            case "docker_restart_plan_dry_run" -> List.of(
                    "docker",
                    "inspect",
                    service.dockerName(args.raw("container")));
            case "docker_image_digest_check" -> List.of(
                    "docker",
                    "inspect",
                    "--format",
                    "{{json .RepoDigests}}",
                    args.required("image", "image digest check 必须提供 image"));
            default -> throw new IllegalArgumentException(
                    "未知 Docker 工具：" + toolName);
        };
        boolean compose = "docker_compose_config".equals(toolName)
                || "docker_compose_config_validate".equals(toolName);
        String workingDirectory = compose ? args.text("composeDir", ".") : ".";
        return Map.of(
                "status", "SUCCEEDED",
                "command", command,
                "output", service.run(
                        command,
                        workingDirectory,
                        settings.allowedDockerComposeRoots(),
                        compose,
                        settings.timeoutSeconds(),
                        settings.maxResponseBytes()));
    }
}
