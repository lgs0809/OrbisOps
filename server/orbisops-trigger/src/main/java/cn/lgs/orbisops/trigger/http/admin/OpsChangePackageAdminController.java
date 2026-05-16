package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.changepackage.ChangePackageApprovalContext;
import cn.lgs.orbisops.application.changepackage.ChangePackageCommands;
import cn.lgs.orbisops.application.changepackage.ChangePackageListQuery;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.changepackage.LandChangePackageUseCase;
import cn.lgs.orbisops.application.changepackage.PrepareChangePackageUseCase;
import cn.lgs.orbisops.application.changepackage.ReviewChangePackageUseCase;
import cn.lgs.orbisops.application.changepackage.ValidateChangePackageUseCase;
import cn.lgs.orbisops.trigger.application.channel.OpsChannelApprovalCardService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackagePermissionService;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ops/change-packages")
public class OpsChangePackageAdminController {

    private final PrepareChangePackageUseCase preparation;
    private final ValidateChangePackageUseCase validation;
    private final ReviewChangePackageUseCase review;
    private final LandChangePackageUseCase landing;
    private final ChangePackageQueryService queries;
    private final OpsChangePackagePermissionService permissionService;
    private final OpsChannelApprovalCardService approvalCards;

    public OpsChangePackageAdminController(PrepareChangePackageUseCase preparation,
                                           ValidateChangePackageUseCase validation,
                                           ReviewChangePackageUseCase review,
                                           LandChangePackageUseCase landing,
                                           ChangePackageQueryService queries,
                                           OpsChangePackagePermissionService permissionService,
                                           OpsChannelApprovalCardService approvalCards) {
        this.preparation = preparation;
        this.validation = validation;
        this.review = review;
        this.landing = landing;
        this.queries = queries;
        this.permissionService = permissionService;
        this.approvalCards = approvalCards;
    }

    @GetMapping("/capabilities")
    public Response<Map<String, Object>> capabilities() {
        return success(queries.capabilities());
    }

    @GetMapping
    public Response<List<Map<String, Object>>> list(@RequestParam(value = "projectId", required = false) String projectId,
                                                     @RequestParam(value = "sessionId", required = false) String sessionId,
                                                     @RequestParam(value = "incidentId", required = false) String incidentId,
                                                     @RequestParam(value = "status", required = false) String status,
                                                     @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit,
                                                     HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        List<Map<String, Object>> rows = queries.list(new ChangePackageListQuery(
                query(projectId, sessionId, incidentId, status), limit == null ? 100 : limit));
        return success(rows.stream().map(row -> withCapabilities(row, principal)).toList());
    }

    @PostMapping
    public Response<Map<String, Object>> create(@RequestBody(required = false) Map<String, Object> request,
                                                 HttpServletRequest servletRequest) {
        permissionService.assertCanPreparePackage(text(request == null ? null : request.get("projectId"), ""), principal(servletRequest));
        return success(preparation.prepare(new ChangePackageCommands.Prepare(request, actor(servletRequest))));
    }

    @PostMapping("/prepare")
    public Response<Map<String, Object>> prepare(@RequestBody(required = false) Map<String, Object> request,
                                                  HttpServletRequest servletRequest) {
        permissionService.assertCanPreparePackage(text(request == null ? null : request.get("projectId"), ""), principal(servletRequest));
        return success(preparation.prepare(new ChangePackageCommands.Prepare(request, actor(servletRequest))));
    }

    @GetMapping("/{packageId}")
    public Response<Map<String, Object>> detail(@PathVariable("packageId") String packageId,
                                                 HttpServletRequest servletRequest) {
        return success(withCapabilities(queries.detail(packageId), principal(servletRequest)));
    }

    @GetMapping("/{packageId}/versions")
    public Response<List<Map<String, Object>>> versions(@PathVariable("packageId") String packageId) {
        return success(queries.versions(packageId));
    }

    @GetMapping("/{packageId}/events")
    public Response<List<Map<String, Object>>> events(@PathVariable("packageId") String packageId,
                                                       @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return success(queries.events(packageId, limit == null ? 100 : limit));
    }

    @GetMapping("/{packageId}/landing-events")
    public Response<List<Map<String, Object>>> landingEvents(@PathVariable("packageId") String packageId,
                                                              @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return events(packageId, limit);
    }

    @GetMapping("/{packageId}/landing-operation-runs")
    public Response<List<Map<String, Object>>> landingOperationRuns(@PathVariable("packageId") String packageId,
                                                                     @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit) {
        return success(queries.landingOperationRuns(packageId, limit == null ? 200 : limit));
    }

    @PostMapping("/{packageId}/revise")
    public Response<Map<String, Object>> revise(@PathVariable("packageId") String packageId,
                                                 @RequestBody(required = false) Map<String, Object> request,
                                                 HttpServletRequest servletRequest) {
        permissionService.assertCanRevisePackage(projectId(packageId), principal(servletRequest));
        return success(preparation.revise(new ChangePackageCommands.Revise(packageId, request, actor(servletRequest))));
    }

    @PostMapping("/{packageId}/validate")
    public Response<Map<String, Object>> validate(@PathVariable("packageId") String packageId,
                                                   @RequestBody(required = false) Map<String, Object> request,
                                                   HttpServletRequest servletRequest) {
        Map<String, Object> detail = queries.detail(packageId);
        permissionService.assertCanRevisePackage(text(detail.get("projectId"), ""), principal(servletRequest));
        int version = intValue(request == null ? null : request.get("version"), intValue(detail.get("version"), 1));
        return success(validation.validate(new ChangePackageCommands.Validate(packageId, version, actor(servletRequest))));
    }

    @PostMapping("/{packageId}/submit-review")
    public Response<Map<String, Object>> submitReview(@PathVariable("packageId") String packageId,
                                                       @RequestBody(required = false) Map<String, Object> request,
                                                       HttpServletRequest servletRequest) {
        permissionService.assertCanRevisePackage(projectId(packageId), principal(servletRequest));
        return success(review.submitReview(new ChangePackageCommands.SubmitReview(packageId, request, actor(servletRequest))));
    }

    @PostMapping("/{packageId}/approve")
    public Response<Map<String, Object>> approve(@PathVariable("packageId") String packageId,
                                                  @RequestBody(required = false) Map<String, Object> request,
                                                  HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        Map<String, Object> detail = queries.detail(packageId);
        int version = intValue(request == null ? null : request.get("version"), intValue(detail.get("version"), 1));
        permissionService.assertCanApprovePackage(text(detail.get("projectId"), ""), principal);
        String packageHash = text(request == null ? null : request.get("packageHash"), "");
        return success(review.approve(new ChangePackageCommands.Approve(packageId, version, packageHash,
                actor(servletRequest), approvalContext(request, principal))));
    }

    @GetMapping("/{packageId}/approval-channels")
    public Response<List<Map<String, Object>>> approvalChannels(@PathVariable("packageId") String packageId,
                                                                 HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        String projectId = projectId(packageId);
        permissionService.assertCanApprovePackage(projectId, principal);
        return success(approvalCards.available(projectId));
    }

    @PostMapping("/{packageId}/approval-card")
    public Response<Map<String, Object>> sendApprovalCard(@PathVariable("packageId") String packageId,
                                                           @RequestBody Map<String, Object> request,
                                                           HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        String projectId = projectId(packageId);
        permissionService.assertCanApprovePackage(projectId, principal);
        return success(approvalCards.send(
                projectId,
                required(request == null ? null : request.get("channelId"), "Channel 不能为空"),
                required(request == null ? null : request.get("target"), "审批卡目标不能为空"),
                packageId,
                actor(servletRequest)));
    }

    @PostMapping("/{packageId}/reject")
    public Response<Map<String, Object>> reject(@PathVariable("packageId") String packageId,
                                                 @RequestBody(required = false) Map<String, Object> request,
                                                 HttpServletRequest servletRequest) {
        permissionService.assertCanRejectPackage(projectId(packageId), principal(servletRequest));
        return success(review.reject(new ChangePackageCommands.Reject(packageId, request, actor(servletRequest))));
    }

    @GetMapping("/{packageId}/landing-plan")
    public Response<Map<String, Object>> landingPlan(@PathVariable("packageId") String packageId) {
        return success(queries.landingPlan(packageId));
    }

    @PostMapping("/{packageId}/land")
    public Response<Map<String, Object>> land(@PathVariable("packageId") String packageId,
                                               @RequestBody(required = false) Map<String, Object> request,
                                               HttpServletRequest servletRequest) {
        permissionService.assertCanLandPackage(projectId(packageId), principal(servletRequest));
        return success(landing.land(new ChangePackageCommands.Land(packageId, request, actor(servletRequest))));
    }

    @PostMapping("/{packageId}/cleanup")
    public Response<Map<String, Object>> cleanup(@PathVariable("packageId") String packageId,
                                                  @RequestBody(required = false) Map<String, Object> request,
                                                  HttpServletRequest servletRequest) {
        permissionService.assertCanLandPackage(projectId(packageId), principal(servletRequest));
        return success(landing.cleanup(new ChangePackageCommands.Cleanup(packageId, request, actor(servletRequest))));
    }

    @PostMapping("/{packageId}/verify-landing")
    public Response<Map<String, Object>> verifyLanding(@PathVariable("packageId") String packageId,
                                                       HttpServletRequest servletRequest) {
        permissionService.assertCanLandPackage(projectId(packageId), principal(servletRequest));
        return success(landing.verifyLanding(packageId, actor(servletRequest)));
    }

    private Map<String, Object> withCapabilities(Map<String, Object> source,
                                                 AdminAuthService.AuthPrincipal principal) {
        Map<String, Object> item = new LinkedHashMap<>(source == null ? Map.of() : source);
        Map<String, Boolean> capabilities = permissionService.capabilities(text(item.get("projectId"), ""), principal);
        item.put("capabilities", capabilities);
        capabilities.forEach(item::put);
        return item;
    }

    private String projectId(String packageId) {
        return text(queries.detail(packageId).get("projectId"), "");
    }

    private Map<String, Object> query(String projectId, String sessionId, String incidentId, String status) {
        Map<String, Object> query = new LinkedHashMap<>();
        putIfText(query, "projectId", projectId);
        putIfText(query, "sessionId", sessionId);
        putIfText(query, "incidentId", incidentId);
        putIfText(query, "status", status);
        return query;
    }

    private void putIfText(Map<String, Object> map, String key, String value) {
        if (StringUtils.hasText(value)) map.put(key, value.trim());
    }

    private String actor(HttpServletRequest request) {
        AdminAuthService.AuthPrincipal principal = principal(request);
        return StringUtils.hasText(principal.userId()) ? principal.userId() : principal.username();
    }

    private AdminAuthService.AuthPrincipal principal(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) return principal;
        throw new SecurityException("未获取到已认证管理员");
    }

    private ChangePackageApprovalContext approvalContext(
            Map<String, Object> request,
            AdminAuthService.AuthPrincipal principal) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new ChangePackageApprovalContext(
                principal.scope(),
                bool(safe.get("adminConfirmation")) || bool(safe.get("adminConfirmed")),
                text(safe.get("comment"), ""));
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value, "");
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private String text(Object value, String fallback) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? fallback : String.valueOf(value).trim();
    }

    private String required(Object value, String message) {
        String normalized = text(value, "");
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
