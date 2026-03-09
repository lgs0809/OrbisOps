package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.incident.IncidentCommandApplicationService;
import cn.lgs.orbisops.application.incident.IncidentQueryApplicationService;
import cn.lgs.orbisops.application.incident.IncidentVerificationApplicationService;
import cn.lgs.orbisops.trigger.application.incident.OpsIncidentMapper;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.OpsIncident;
import cn.lgs.orbisops.trigger.ops.OpsIncidentTimelineItem;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
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

/**
 * 运维事件管理接口。
 */
@RestController
@RequestMapping("/api/v1/admin/ops")
public class OpsIncidentAdminController {

    private final IncidentQueryApplicationService queries;
    private final IncidentCommandApplicationService commands;
    private final IncidentVerificationApplicationService verification;
    private final OpsIncidentMapper mapper;

    public OpsIncidentAdminController(
            IncidentQueryApplicationService queries,
            IncidentCommandApplicationService commands,
            IncidentVerificationApplicationService verification,
            OpsIncidentMapper mapper) {
        this.queries = queries;
        this.commands = commands;
        this.verification = verification;
        this.mapper = mapper;
    }

    @GetMapping("/incidents")
    public Response<List<OpsIncident>> listIncidents(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit) {
        return Response.<List<OpsIncident>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(mapper.incidents(queries.list(
                        projectId, status, Optional.ofNullable(limit).orElse(50))))
                .build();
    }

    @PostMapping("/incidents")
    public Response<OpsIncident> createIncident(
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        OpsIncident incident = mapper.incident(commands.create(
                mapper.createCommand(request == null ? Map.of() : request),
                actor(servletRequest)));
        return Response.<OpsIncident>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(incident)
                .build();
    }

    @GetMapping("/incidents/{incidentId}")
    public Response<OpsIncident> getIncident(@PathVariable("incidentId") String incidentId) {
        return Response.<OpsIncident>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(queries.get(incidentId).map(mapper::incident).orElse(null))
                .build();
    }

    @GetMapping("/incidents/{incidentId}/detail")
    public Response<Map<String, Object>> getIncidentDetail(@PathVariable("incidentId") String incidentId) {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(queries.detail(incidentId).map(mapper::detail).orElse(Map.of()))
                .build();
    }

    @PutMapping("/incidents/{incidentId}/status")
    public Response<OpsIncident> updateIncidentStatus(
            @PathVariable("incidentId") String incidentId,
            @RequestParam("status") String status,
            @RequestParam(value = "note", required = false) String note,
            HttpServletRequest servletRequest) {
        OpsIncident incident = mapper.incident(commands.updateStatus(
                incidentId, status, actor(servletRequest), note));
        return Response.<OpsIncident>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(incident)
                .build();
    }

    @PutMapping("/incidents/{incidentId}/owner")
    public Response<OpsIncident> assignIncidentOwner(
            @PathVariable("incidentId") String incidentId,
            @RequestParam(value = "ownerUserId", required = false) String ownerUserId,
            HttpServletRequest servletRequest) {
        return Response.<OpsIncident>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(mapper.incident(commands.assignOwner(
                        incidentId, ownerUserId, actor(servletRequest))))
                .build();
    }

    @PostMapping("/incidents/{incidentId}/comments")
    public Response<OpsIncidentTimelineItem> addIncidentComment(
            @PathVariable("incidentId") String incidentId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        String comment = request == null ? "" : String.valueOf(request.getOrDefault("comment", ""));
        return Response.<OpsIncidentTimelineItem>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(mapper.timeline(commands.addComment(incidentId, comment, actor(servletRequest))))
                .build();
    }

    @PutMapping("/incidents/{incidentId}/watchers/{userId}")
    public Response<Boolean> addIncidentWatcher(
            @PathVariable("incidentId") String incidentId,
            @PathVariable("userId") String userId,
            HttpServletRequest servletRequest) {
        commands.addWatcher(incidentId, userId, actor(servletRequest));
        return success(true);
    }

    @DeleteMapping("/incidents/{incidentId}/watchers/{userId}")
    public Response<Boolean> removeIncidentWatcher(
            @PathVariable("incidentId") String incidentId,
            @PathVariable("userId") String userId,
            HttpServletRequest servletRequest) {
        commands.removeWatcher(incidentId, userId, actor(servletRequest));
        return success(true);
    }

    @PutMapping("/incidents/{incidentId}/relations/{relatedIncidentId}")
    public Response<Boolean> relateIncident(
            @PathVariable("incidentId") String incidentId,
            @PathVariable("relatedIncidentId") String relatedIncidentId,
            HttpServletRequest servletRequest) {
        commands.relate(incidentId, relatedIncidentId, actor(servletRequest));
        return success(true);
    }

    @DeleteMapping("/incidents/{incidentId}/relations/{relatedIncidentId}")
    public Response<Boolean> unrelateIncident(
            @PathVariable("incidentId") String incidentId,
            @PathVariable("relatedIncidentId") String relatedIncidentId,
            HttpServletRequest servletRequest) {
        commands.unrelate(incidentId, relatedIncidentId, actor(servletRequest));
        return success(true);
    }

    @GetMapping("/incidents/{incidentId}/timeline")
    public Response<List<OpsIncidentTimelineItem>> incidentTimeline(
            @PathVariable("incidentId") String incidentId,
            @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return Response.<List<OpsIncidentTimelineItem>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(mapper.timeline(queries.timeline(
                        incidentId, Optional.ofNullable(limit).orElse(100))))
                .build();
    }

    @GetMapping("/incidents/{incidentId}/runs")
    public Response<List<Map<String, Object>>> incidentRuns(
            @PathVariable("incidentId") String incidentId) {
        return Response.<List<Map<String, Object>>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(mapper.runs(queries.runs(incidentId)))
                .build();
    }

    @PostMapping("/incidents/{incidentId}/timeline")
    public Response<OpsIncidentTimelineItem> appendIncidentTimeline(
            @PathVariable("incidentId") String incidentId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        OpsIncidentTimelineItem item = mapper.timeline(commands.appendUserTimeline(
                incidentId,
                mapper.timelineCommand(request == null ? Map.of() : request),
                actor(servletRequest)));
        return Response.<OpsIncidentTimelineItem>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(item)
                .build();
    }

    @PostMapping("/incidents/{incidentId}/feedback/helpful")
    public Response<OpsIncidentTimelineItem> confirmIncidentHelpful(
            @PathVariable("incidentId") String incidentId,
            HttpServletRequest servletRequest) {
        return Response.<OpsIncidentTimelineItem>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(mapper.timeline(commands.confirmHelpful(incidentId, actor(servletRequest))))
                .build();
    }

    @PostMapping("/incidents/{incidentId}/verification")
    public Response<Map<String, Object>> verifyIncident(
            @PathVariable("incidentId") String incidentId,
            @RequestParam("packageId") String packageId,
            HttpServletRequest servletRequest) {
        var result = verification.verify(incidentId, packageId, actor(servletRequest));
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(Map.of(
                        "status", result.status().name(),
                        "summary", result.summary(),
                        "evidence", result.evidence()))
                .build();
    }

    @PostMapping("/incidents/{incidentId}/runs/{runId}")
    public Response<Boolean> linkIncidentRun(
            @PathVariable("incidentId") String incidentId,
            @PathVariable("runId") String runId,
            @RequestParam(value = "detail", required = false,
                    defaultValue = "人工关联运维分析 run") String detail,
            HttpServletRequest servletRequest) {
        commands.linkRun(incidentId, runId, actor(servletRequest), detail);
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(true)
                .build();
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
