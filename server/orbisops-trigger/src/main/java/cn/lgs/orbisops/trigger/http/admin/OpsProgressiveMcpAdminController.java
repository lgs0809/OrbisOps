package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.mcp.DiscoverMcpToolsProcessManager;
import cn.lgs.orbisops.application.mcp.McpPolicyQueryService;
import cn.lgs.orbisops.application.mcp.McpRuntimeHistoryQueryService;
import cn.lgs.orbisops.application.mcp.McpToolSnapshotQueryService;
import cn.lgs.orbisops.application.mcp.ReviewMcpPolicyUseCase;
import cn.lgs.orbisops.trigger.application.mcp.OpsMcpAuthoritativeSchemaHydrationService;
import cn.lgs.orbisops.trigger.application.mcp.OpsMcpDiscoverySelectionMapper;
import cn.lgs.orbisops.trigger.application.mcp.OpsMcpToolPolicyCommandMapper;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ops/projects/{projectId}")
public class OpsProgressiveMcpAdminController {

    private final DiscoverMcpToolsProcessManager discovery;
    private final McpRuntimeHistoryQueryService historyQueries;
    private final McpToolSnapshotQueryService snapshotQueries;
    private final McpPolicyQueryService policyQueries;
    private final ReviewMcpPolicyUseCase review;
    private final OpsMcpAuthoritativeSchemaHydrationService authoritativeSchemaHydration;
    private final OpsMcpDiscoverySelectionMapper selectionMapper;
    private final OpsMcpToolPolicyCommandMapper policyCommandMapper;

    public OpsProgressiveMcpAdminController(DiscoverMcpToolsProcessManager discovery,
                                             McpRuntimeHistoryQueryService historyQueries,
                                             McpToolSnapshotQueryService snapshotQueries,
                                             McpPolicyQueryService policyQueries,
                                             ReviewMcpPolicyUseCase review,
                                             OpsMcpAuthoritativeSchemaHydrationService authoritativeSchemaHydration,
                                             OpsMcpDiscoverySelectionMapper selectionMapper,
                                             OpsMcpToolPolicyCommandMapper policyCommandMapper) {
        this.discovery = discovery;
        this.historyQueries = historyQueries;
        this.snapshotQueries = snapshotQueries;
        this.policyQueries = policyQueries;
        this.review = review;
        this.authoritativeSchemaHydration = authoritativeSchemaHydration;
        this.selectionMapper = selectionMapper;
        this.policyCommandMapper = policyCommandMapper;
    }

    @GetMapping("/tool-catalog-summary")
    public Response<Map<String, Object>> summary(@PathVariable("projectId") String projectId) {
        return success(discovery.summary(projectId));
    }

    @PostMapping("/tool-catalog-summary/rebuild")
    public Response<Map<String, Object>> rebuild(@PathVariable("projectId") String projectId) {
        return success(discovery.rebuildSummary(projectId));
    }

    @PostMapping("/tool-router/select")
    public Response<Map<String, Object>> select(@PathVariable("projectId") String projectId,
                                                 @RequestBody(required = false) Map<String, Object> request) {
        return success(discovery.select(selectionMapper.map(projectId, request)).view(request));
    }

    @PostMapping("/tool-router/hydrate-schema")
    public Response<Map<String, Object>> hydrateSchema(@PathVariable("projectId") String projectId,
                                                        @RequestBody(required = false) Map<String, Object> request) {
        return success(authoritativeSchemaHydration.hydrate(projectId, request).view());
    }

    @GetMapping("/tool-router/decisions")
    public Response<List<Map<String, Object>>> decisions(@PathVariable("projectId") String projectId,
                                                          @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return success(historyQueries.decisions(projectId, limit == null ? 100 : limit));
    }

    @GetMapping("/mcp-tool-calls")
    public Response<List<Map<String, Object>>> mcpCalls(@PathVariable("projectId") String projectId,
                                                         @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return success(historyQueries.calls(projectId, limit == null ? 100 : limit));
    }

    @GetMapping("/mcp-tool-calls/{callId}")
    public Response<Map<String, Object>> mcpCall(@PathVariable("projectId") String projectId,
                                                  @PathVariable("callId") String callId) {
        return success(historyQueries.call(projectId, callId));
    }

    @GetMapping("/mcp-tool-activations")
    public Response<List<Map<String, Object>>> activations(@PathVariable("projectId") String projectId,
                                                            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return success(historyQueries.runtimeActivations(projectId, limit == null ? 100 : limit));
    }

    @GetMapping("/mcp-tool-snapshots")
    public Response<List<Map<String, Object>>> toolSnapshots(@PathVariable("projectId") String projectId,
                                                              @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return success(snapshotQueries.snapshots(projectId, limit == null ? 100 : limit));
    }

    @GetMapping("/mcp-tool-policies")
    public Response<List<Map<String, Object>>> toolPolicies(@PathVariable("projectId") String projectId,
                                                             @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return success(policyQueries.policies(projectId, limit == null ? 100 : limit));
    }

    @PostMapping("/mcp-tool-policies")
    public Response<Map<String, Object>> upsertToolPolicy(@PathVariable("projectId") String projectId,
                                                           @RequestBody(required = false) Map<String, Object> request,
                                                           HttpServletRequest servletRequest) {
        return success(policyQueries.view(review.upsert(policyCommandMapper.mutation(
                projectId, "", actor(servletRequest), request))));
    }

    @PostMapping("/mcp-tool-policies/{policyId}/approve")
    public Response<Map<String, Object>> approveToolPolicy(@PathVariable("projectId") String projectId,
                                                            @PathVariable("policyId") String policyId,
                                                            @RequestBody(required = false) Map<String, Object> request,
                                                            HttpServletRequest servletRequest) {
        return success(policyQueries.view(review.approve(policyCommandMapper.mutation(
                projectId, policyId, actor(servletRequest), request))));
    }

    @PostMapping("/mcp-tool-policies/{policyId}/reject")
    public Response<Map<String, Object>> rejectToolPolicy(@PathVariable("projectId") String projectId,
                                                           @PathVariable("policyId") String policyId,
                                                           @RequestBody(required = false) Map<String, Object> request,
                                                           HttpServletRequest servletRequest) {
        return success(policyQueries.view(review.reject(policyCommandMapper.mutation(
                projectId, policyId, actor(servletRequest), request))));
    }

    @PatchMapping("/mcp-tool-policies/{policyId}/disable")
    public Response<Map<String, Object>> disableToolPolicy(@PathVariable("projectId") String projectId,
                                                            @PathVariable("policyId") String policyId,
                                                            @RequestBody(required = false) Map<String, Object> request,
                                                            HttpServletRequest servletRequest) {
        return success(policyQueries.view(review.disable(policyCommandMapper.mutation(
                projectId, policyId, actor(servletRequest), request))));
    }

    private String actor(HttpServletRequest request) {
        Object value = request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) {
            return principal.username();
        }
        throw new IllegalStateException("未获取到已认证操作者");
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
