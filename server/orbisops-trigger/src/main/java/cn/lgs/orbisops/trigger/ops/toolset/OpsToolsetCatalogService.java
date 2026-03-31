package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import cn.lgs.orbisops.application.toolset.ToolsetRefreshOutcome;
import cn.lgs.orbisops.domain.toolset.model.ToolsetRefreshStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OpsToolsetCatalogService {

    private final OpsToolsetRegistry registry;
    private final OpsCustomToolsetService customToolsetService;
    private final ProgressiveMcpProcessManager progressiveMcpProcessManager;

    public OpsToolsetCatalogService(OpsToolsetRegistry registry,
                                    OpsCustomToolsetService customToolsetService,
                                    ObjectProvider<ProgressiveMcpProcessManager> progressiveMcpProcessManagerProvider) {
        this.registry = registry;
        this.customToolsetService = customToolsetService;
        this.progressiveMcpProcessManager = progressiveMcpProcessManagerProvider.getIfAvailable();
    }

    public List<OpsToolsetDefinition> listBuiltInToolsets() {
        return registry.listBuiltInToolsets();
    }

    public List<OpsToolsetDefinition> listCustomToolsets(String projectId) {
        return customToolsetService.listCustomToolsets(projectId);
    }

    public List<OpsToolsetDefinition> listEffectiveToolsets(String projectId, String userId) {
        return registry.listEffectiveToolsets(projectId, customToolsetService.listCustomToolsets(projectId));
    }

    public OpsToolsetDefinition registerCustomToolset(String projectId, Map<String, Object> request, String actor) {
        return customToolsetService.registerCustomToolset(projectId, request, actor);
    }

    public void enableToolset(String projectId, String toolsetId, String actor) {
        customToolsetService.setEnabled(projectId, toolsetId, true, actor);
    }

    public void disableToolset(String projectId, String toolsetId, String actor) {
        customToolsetService.setEnabled(projectId, toolsetId, false, actor);
    }

    /**
     * MCP discovery is currently the only stable long-lived Toolset refresh source.
     * Keep this source-specific operation explicit. Extract a ToolsetRefresher strategy
     * only after a second production source (for example OpenAPI import, local adapter
     * scan or plugin installation) has its own lifecycle and refresh contract.
     */
    public ToolsetRefreshOutcome refreshMcpToolset(
            String projectId,
            String toolsetId,
            String actor) {
        if (progressiveMcpProcessManager == null) {
            return new ToolsetRefreshOutcome(
                    projectId,
                    toolsetId,
                    ToolsetRefreshStatus.REFRESH_UNAVAILABLE,
                    "Progressive MCP service is not available; no remote refresh was executed.",
                    Map.of());
        }
        Map<String, Object> summary = progressiveMcpProcessManager.rebuildSummary(projectId);
        return new ToolsetRefreshOutcome(
                projectId,
                toolsetId,
                ToolsetRefreshStatus.REFRESHED,
                "",
                summary);
    }
}
