package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.incident.AppendIncidentTimelineCommand;
import cn.lgs.orbisops.application.incident.CreateIncidentCommand;
import cn.lgs.orbisops.application.incident.IncidentCommandApplicationService;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.application.incident.IncidentVerificationApplicationService;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.trigger.application.incident.OpsIncidentMapper;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.OpsIncident;
import cn.lgs.orbisops.trigger.ops.OpsIncidentTimelineItem;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Project-scoped Incident surface for ordinary authenticated users. */
@RestController
@RequestMapping("/api/v1/user/ops/incidents")
public class OpsUserIncidentController {

    private final IncidentQueryApplicationService queries;
    private final IncidentCommandApplicationService commands;
    private final IncidentVerificationApplicationService verification;
    private final OpsIncidentMapper mapper;
    private final AuthorizeProjectAccessUseCase projectAccess;

    public OpsUserIncidentController(
            IncidentQueryApplicationService queries,
            IncidentCommandApplicationService commands,
            IncidentVerificationApplicationService verification,
            OpsIncidentMapper mapper,
            AuthorizeProjectAccessUseCase projectAccess) {
        this.queries = queries;
        this.commands = commands;
        this.verification = verification;
        this.mapper = mapper;
        this.projectAccess = projectAccess;
    }

    @GetMapping
    public Response<List<OpsIncident>> list(
            @RequestParam("projectId") String projectId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit,
            HttpServletRequest request) {
        requireProject(projectId, request);
        return success(mapper.incidents(queries.list(
                projectId, status, Optional.ofNullable(limit).orElse(50))));
    }

    @PostMapping
    public Response<OpsIncident> create(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        CreateIncidentCommand command = mapper.createCommand(body == null ? Map.of() : body);
        requireProject(command.projectId(), request);
        return success(mapper.incident(commands.create(command, actor(principal(request)))));
    }

    @GetMapping("/{incidentId}")
    public Response<OpsIncident> get(@PathVariable String incidentId, HttpServletRequest request) {
        return success(mapper.incident(authorizedIncident(incidentId, request)));
    }

    @GetMapping("/{incidentId}/detail")
    public Response<Map<String, Object>> detail(@PathVariable String incidentId, HttpServletRequest request) {
        authorizedIncident(incidentId, request);
        String currentActor = actor(principal(request));
        Map<String, Object> detail = new java.util.LinkedHashMap<>(
                queries.detail(incidentId).map(mapper::detail).orElse(Map.of()));
        detail.put("currentUserWatching", queries.watchers(incidentId).stream()
                .anyMatch(item -> currentActor.equalsIgnoreCase(item.userId())));
        return success(detail);
    }

    @PutMapping("/{incidentId}/status")
    public Response<OpsIncident> updateStatus(
            @PathVariable String incidentId,
            @RequestParam("status") String status,
            @RequestParam(value = "note", required = false) String note,
            HttpServletRequest request) {
        authorizedIncident(incidentId, request);
        return success(mapper.incident(commands.updateStatus(
                incidentId, status, actor(principal(request)), note)));
    }

    @PutMapping("/{incidentId}/owner")
    public Response<OpsIncident> claimOwner(
            @PathVariable String incidentId,
            HttpServletRequest request) {
        IncidentSnapshot incident = authorizedIncident(incidentId, request);
        String currentActor = actor(principal(request));
        if (StringUtils.hasText(incident.ownerUserId())
                && !incident.ownerUserId().equals(currentActor)) {
            throw new SecurityException("INCIDENT_ALREADY_OWNED_BY_OTHER_USER");
        }
        return success(mapper.incident(commands.assignOwner(
                incidentId, currentActor, currentActor)));
    }

    @PostMapping("/{incidentId}/comments")
    public Response<OpsIncidentTimelineItem> addComment(
            @PathVariable String incidentId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        authorizedIncident(incidentId, request);
        String comment = body == null ? "" : String.valueOf(body.getOrDefault("comment", ""));
        return success(mapper.timeline(commands.addComment(
                incidentId, comment, actor(principal(request)))));
    }

    @PutMapping("/{incidentId}/watchers/me")
    public Response<Boolean> watch(
            @PathVariable String incidentId,
            HttpServletRequest request) {
        authorizedIncident(incidentId, request);
        String currentActor = actor(principal(request));
        commands.addWatcher(incidentId, currentActor, currentActor);
        return success(true);
    }

    @DeleteMapping("/{incidentId}/watchers/me")
    public Response<Boolean> unwatch(
            @PathVariable String incidentId,
            HttpServletRequest request) {
        authorizedIncident(incidentId, request);
        String currentActor = actor(principal(request));
        commands.removeWatcher(incidentId, currentActor, currentActor);
        return success(true);
    }

    @PutMapping("/{incidentId}/relations/{relatedIncidentId}")
    public Response<Boolean> relate(
            @PathVariable String incidentId,
            @PathVariable String relatedIncidentId,
            HttpServletRequest request) {
        authorizedIncident(incidentId, request);
        IncidentSnapshot related = authorizedIncident(relatedIncidentId, request);
        IncidentSnapshot source = queries.get(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + incidentId));
        if (!source.projectId().equals(related.projectId())) {
            throw new SecurityException("INCIDENT_RELATION_PROJECT_MISMATCH");
        }
        commands.relate(incidentId, relatedIncidentId, actor(principal(request)));
        return success(true);
    }

    @DeleteMapping("/{incidentId}/relations/{relatedIncidentId}")
    public Response<Boolean> unrelate(
            @PathVariable String incidentId,
            @PathVariable String relatedIncidentId,
            HttpServletRequest request) {
        authorizedIncident(incidentId, request);
        authorizedIncident(relatedIncidentId, request);
        commands.unrelate(incidentId, relatedIncidentId, actor(principal(request)));
        return success(true);
    }

    @GetMapping("/{incidentId}/timeline")
    public Response<List<OpsIncidentTimelineItem>> timeline(
            @PathVariable String incidentId,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit,
            HttpServletRequest request) {
        authorizedIncident(incidentId, request);
        return success(mapper.timeline(queries.timeline(
                incidentId, Optional.ofNullable(limit).orElse(100))));
    }

    @PostMapping("/{incidentId}/timeline")
    public Response<OpsIncidentTimelineItem> appendUserTimeline(
            @PathVariable String incidentId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        authorizedIncident(incidentId, request);
        AppendIncidentTimelineCommand command = mapper.timelineCommand(body == null ? Map.of() : body);
        return success(mapper.timeline(commands.appendUserTimeline(
                incidentId, command, actor(principal(request)))));
    }

    @PostMapping("/{incidentId}/feedback/helpful")
    public Response<OpsIncidentTimelineItem> confirmHelpful(
            @PathVariable String incidentId,
            HttpServletRequest request) {
        authorizedIncident(incidentId, request);
        return success(mapper.timeline(commands.confirmHelpful(
                incidentId, actor(principal(request)))));
    }

    @PostMapping("/{incidentId}/verification")
    public Response<Map<String, Object>> verifyIncident(
            @PathVariable String incidentId,
            @RequestParam("packageId") String packageId,
            HttpServletRequest request) {
        authorizedIncident(incidentId, request);
        var result = verification.verify(incidentId, packageId, actor(principal(request)));
        return success(Map.of(
                "status", result.status().name(),
                "summary", result.summary(),
                "evidence", result.evidence()));
    }

    @GetMapping("/{incidentId}/runs")
    public Response<List<Map<String, Object>>> runs(
            @PathVariable String incidentId,
            HttpServletRequest request) {
        authorizedIncident(incidentId, request);
        return success(mapper.runs(queries.runs(incidentId)));
    }

    @PostMapping("/{incidentId}/runs/{runId}")
    public Response<Boolean> linkRun(
            @PathVariable String incidentId,
            @PathVariable String runId,
            @RequestParam(value = "detail", required = false,
                    defaultValue = "AI 对话关联运维分析 run") String detail,
            HttpServletRequest request) {
        authorizedIncident(incidentId, request);
        commands.linkRun(incidentId, runId, actor(principal(request)), detail);
        return success(true);
    }

    private IncidentSnapshot authorizedIncident(String incidentId, HttpServletRequest request) {
        IncidentSnapshot incident = queries.get(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + incidentId));
        requireProject(incident.projectId(), request);
        return incident;
    }

    private void requireProject(String projectId, HttpServletRequest request) {
        AdminAuthService.AuthPrincipal principal = principal(request);
        projectAccess.requireAccess(
                projectId,
                principal.username(),
                principal.userId(),
                AdminAuthService.SCOPE_ADMIN.equals(principal.scope()));
    }

    private AdminAuthService.AuthPrincipal principal(HttpServletRequest request) {
        Object value = request == null
                ? null
                : request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) return principal;
        throw new SecurityException("未获取到已认证用户");
    }

    private String actor(AdminAuthService.AuthPrincipal principal) {
        return StringUtils.hasText(principal.userId()) ? principal.userId() : principal.username();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
