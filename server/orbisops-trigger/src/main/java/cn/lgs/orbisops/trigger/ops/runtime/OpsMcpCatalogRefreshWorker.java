package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.mcp.McpToolCatalogStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Read-only catalog refresh. Resolve current protected configuration instead of retaining a Run's authority. */
@Component
public final class OpsMcpCatalogRefreshWorker {
    private static final Logger log=LoggerFactory.getLogger(OpsMcpCatalogRefreshWorker.class);
    private final McpToolCatalogStore catalogs;
    private final OpsMcpRuntimeConfigSourceChain sources;
    private final OpsMcpRemoteClientAdapter remote;
    public OpsMcpCatalogRefreshWorker(McpToolCatalogStore catalogs,OpsMcpRuntimeConfigSourceChain sources,OpsMcpRemoteClientAdapter remote) {
        this.catalogs=catalogs;this.sources=sources;this.remote=remote;
    }
    @Scheduled(cron="${orbisops.mcp.catalog.refresh-cron:0 0 3 * * *}",zone="${orbisops.mcp.catalog.refresh-zone:Asia/Shanghai}")
    public void refresh() {
        String cursor="";
        var checkedSources=new java.util.HashSet<java.util.List<String>>();
        while (true) {
            var page=catalogs.scopesAfter(cursor,100);
            if (page.isEmpty()) return;
            for (var scope:page) {
                // Credential/configuration revisions remain in history, but resolve and refresh
                // the currently authorized connection only once in this sweep.
                if (!checkedSources.add(java.util.List.of(scope.projectId(),scope.serverId()))) continue;
                var started=java.time.Instant.now();
                try {
                    var resolved=sources.resolve(new OpsMcpRuntimeConfigRequest(scope.projectId(),scope.serverId()));
                    if (!resolved.matched()) {
                        catalogs.failed(scope,started,"MCP_CATALOG_SOURCE_UNAVAILABLE");
                        continue;
                    }
                    remote.refreshCatalog(resolved.config());
                } catch (RuntimeException failed) {
                    catalogs.failed(scope,started,"MCP_CATALOG_REFRESH_FAILED");
                    log.warn("MCP catalog refresh failed, project={}, server={}, type={}",scope.projectId(),scope.serverId(),failed.getClass().getSimpleName());
                }
            }
            cursor=page.get(page.size()-1).identity();
        }
    }
}
