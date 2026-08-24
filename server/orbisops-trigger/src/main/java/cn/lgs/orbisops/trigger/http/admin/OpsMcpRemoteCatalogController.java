package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.mcp.McpToolCatalogService;
import cn.lgs.orbisops.application.mcp.McpToolCatalogStore.Status;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Project-scoped read-only status. No credentials or remote tool bodies are returned. */
@RestController
@RequestMapping("/api/v1/admin/ops/projects/{projectId}/mcp-remote-catalogs")
public class OpsMcpRemoteCatalogController {
    private final McpToolCatalogService catalogs;
    public OpsMcpRemoteCatalogController(McpToolCatalogService catalogs) { this.catalogs=catalogs; }
    @GetMapping public Response<List<Status>> status(@PathVariable("projectId") String projectId) {
        return Response.<List<Status>>builder().code("0000").info("成功").data(catalogs.status(projectId)).build();
    }
}
