package cn.lgs.orbisops.trigger.application.source;

import cn.lgs.orbisops.application.project.ProjectMcpProjectionApplicationService;
import cn.lgs.orbisops.application.source.SourceMcpProjectionPort;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsSourceMcpProjectionAdapter implements SourceMcpProjectionPort {

    private final ProjectMcpProjectionApplicationService projections;
    private final OpsSourceMcpProjectionSettings settings;

    public OpsSourceMcpProjectionAdapter(
            ProjectMcpProjectionApplicationService projections,
            OpsSourceMcpProjectionSettings settings) {
        if (projections == null) throw new IllegalArgumentException("SOURCE_MCP_PROJECTIONS_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("SOURCE_MCP_PROJECTION_SETTINGS_REQUIRED");
        this.projections = projections;
        this.settings = settings;
    }

    @Override
    public void publish(SourceRepository repository) {
        if (repository == null) return;
        Map<String, Object> transportConfig = new LinkedHashMap<>();
        transportConfig.put("generated", true);
        transportConfig.put("serverTemplate", "git-readonly-mcp");
        transportConfig.put("repositoryId", repository.repositoryId());
        transportConfig.put("defaultCommitSha", repository.defaultCommitSha());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("mcpId", repository.mcpId());
        view.put("mcpName", repository.name() + " 只读 Git MCP");
        view.put("projectId", repository.projectId());
        view.put("resourceId", repository.repositoryId());
        view.put("resourceType", "git");
        view.put("transportType", "stdio");
        view.put("templateId", "git-readonly-template");
        view.put("transportConfig", transportConfig);
        view.put("allowedActions", List.of(
                "git_repository_info", "git_read_file", "git_search_code",
                "git_list_files", "git_diff_summary"));
        view.put("riskLevel", "LOW");
        view.put("readOnly", true);
        view.put("permissionPolicy", Map.of("readOnly", true, "source", "SOURCE_REPOSITORY_CATALOG"));
        view.put("requestTimeout", settings.requestTimeoutSeconds());
        view.put("status", repository.ready() ? "ENABLED" : "DISABLED");
        view.put("createdAt", repository.createdAt());
        view.put("updatedAt", repository.updatedAt());
        projections.publish(view);
    }
}
