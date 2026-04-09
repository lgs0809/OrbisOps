package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.incident.AlertCorrelationApplicationService;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.domain.incident.correlation.CorrelationTopologyEdge;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class OpsAlertCorrelationController {
    private final AlertCorrelationApplicationService correlation;
    private final AuthorizeProjectAccessUseCase access;
    public OpsAlertCorrelationController(AlertCorrelationApplicationService correlation, AuthorizeProjectAccessUseCase access) {
        this.correlation = correlation; this.access = access;
    }
    @GetMapping({"/api/v1/user/ops/alert-correlations", "/api/v1/admin/ops/alert-correlations"})
    public Response<List<Map<String, Object>>> groups(@RequestParam String projectId,
            @RequestParam(defaultValue = "") String environment, @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest request) {
        authorize(projectId, request);
        return success(correlation.groups(projectId, environment, limit));
    }
    @GetMapping("/api/v1/admin/ops/alert-correlations/topology")
    public Response<List<Map<String, String>>> topology(@RequestParam String projectId, @RequestParam String environment,
            HttpServletRequest request) {
        authorize(projectId, request);
        return success(correlation.topology(projectId, environment).stream().map(edge -> Map.of(
                "source", edge.source(), "target", edge.target(), "evidenceRef", edge.evidenceRef(),
                "observedAt", edge.observedAt().toString(), "expiresAt", edge.expiresAt().toString())).toList());
    }
    @PutMapping("/api/v1/admin/ops/alert-correlations/topology")
    public Response<Boolean> configure(@RequestBody TopologyInput body, HttpServletRequest request) {
        AdminAuthService.AuthPrincipal principal = authorize(body.projectId(), request);
        if (!AdminAuthService.SCOPE_ADMIN.equals(principal.scope())) throw new SecurityException("CORRELATION_ADMIN_REQUIRED");
        if (body.edges() == null || body.edges().size() > 256) throw new IllegalArgumentException("CORRELATION_TOPOLOGY_LIMIT_EXCEEDED");
        List<CorrelationTopologyEdge> edges = body.edges().stream().map(edge -> new CorrelationTopologyEdge(
                requiredEdge(edge).source(), edge.target(), edge.evidenceRef(), timestamp(edge.observedAt()), timestamp(edge.expiresAt()))).toList();
        correlation.configure(body.projectId(), body.environment(), edges, principal.username());
        return success(true);
    }
    private AdminAuthService.AuthPrincipal authorize(String project, HttpServletRequest request) {
        if (!(request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE) instanceof AdminAuthService.AuthPrincipal principal))
            throw new SecurityException("CORRELATION_AUTH_REQUIRED");
        access.requireAccess(project, principal.username(), principal.userId(), AdminAuthService.SCOPE_ADMIN.equals(principal.scope()));
        return principal;
    }
    private <T> Response<T> success(T data) {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode()).info(ResponseCode.SUCCESS.getInfo()).data(data).build();
    }
    public record TopologyInput(String projectId, String environment, List<EdgeInput> edges) { }
    public record EdgeInput(String source, String target, String evidenceRef, String observedAt, String expiresAt) { }
    private EdgeInput requiredEdge(EdgeInput edge) {
        if (edge == null) throw new IllegalArgumentException("CORRELATION_TOPOLOGY_EDGE_REQUIRED");
        return edge;
    }
    private Instant timestamp(String value) {
        try {
            if (value != null) return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException invalid) {
            throw new IllegalArgumentException("CORRELATION_TOPOLOGY_TIMESTAMP_INVALID", invalid);
        }
        throw new IllegalArgumentException("CORRELATION_TOPOLOGY_TIMESTAMP_REQUIRED");
    }
}
