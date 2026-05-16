package cn.lgs.orbisops.trigger.http.agent;

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
@RequestMapping("/api/v1/user")
public class OpsUserChangePackageController {

    private final PrepareChangePackageUseCase preparation;
    private final ValidateChangePackageUseCase validation;
    private final ReviewChangePackageUseCase review;
    private final LandChangePackageUseCase landing;
    private final ChangePackageQueryService queries;
    private final OpsChangePackagePermissionService permissionService;
    private final OpsChannelApprovalCardService approvalCards;

    public OpsUserChangePackageController(PrepareChangePackageUseCase preparation,
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

    @GetMapping("/ops/change-packages")
    public Response<List<Map<String, Object>>> list(@RequestParam(value = "projectId", required = false) String projectId,
                                                     @RequestParam(value = "sessionId", required = false) String sessionId,
                                                     @RequestParam(value = "status", required = false) String status,
                                                     @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit,
                                                     HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        if (!StringUtils.hasText(projectId)) {
            throw new IllegalArgumentException("用户查询 ChangePackage 必须提供 projectId，避免跨项目数据泄漏");
        }
        permissionService.assertCanViewPackage(projectId, principal);
        Map<String, Object> filters = new LinkedHashMap<>();
        putIfText(filters, "projectId", projectId);
        putIfText(filters, "sessionId", sessionId);
        putIfText(filters, "status", status);
        String normalizedProjectId = projectId.trim();
        List<Map<String, Object>> rows = queries.list(new ChangePackageListQuery(filters, limit == null ? 100 : limit));
        return success(rows.stream()
                .filter(row -> normalizedProjectId.equals(text(row.get("projectId"), "")))
                .map(row -> withCapabilities(row, principal))
                .toList());
    }

    @PostMapping("/ops/change-packages")
    public Response<Map<String, Object>> prepare(@RequestBody(required = false) Map<String, Object> request,
                                                  HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        permissionService.assertCanPreparePackage(text(request == null ? null : request.get("projectId"), ""), principal);
        return success(preparation.prepare(new ChangePackageCommands.Prepare(request, actor(principal))));
    }

    @PostMapping("/chat/sessions/{sessionId}/change-packages")
    public Response<Map<String, Object>> createFromSession(@PathVariable("sessionId") String sessionId,
                                                            @RequestBody(required = false) Map<String, Object> request,
                                                            HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        permissionService.assertCanPreparePackage(text(request == null ? null : request.get("projectId"), ""), principal);
        return success(preparation.prepareForSession(
                new ChangePackageCommands.PrepareForSession(sessionId, request, actor(principal))));
    }

    @GetMapping("/ops/change-packages/{packageId}")
    public Response<Map<String, Object>> detail(@PathVariable("packageId") String packageId,
                                                 HttpServletRequest servletRequest) {
        Map<String, Object> detail = authorizedDetail(packageId, servletRequest);
        return success(detail);
    }

    @GetMapping("/ops/change-packages/{packageId}/versions")
    public Response<List<Map<String, Object>>> versions(@PathVariable("packageId") String packageId,
                                                         HttpServletRequest servletRequest) {
        authorizedDetail(packageId, servletRequest);
        return success(queries.versions(packageId));
    }

    @GetMapping("/ops/change-packages/{packageId}/events")
    public Response<List<Map<String, Object>>> events(@PathVariable("packageId") String packageId,
                                                       @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit,
                                                       HttpServletRequest servletRequest) {
        authorizedDetail(packageId, servletRequest);
        return success(queries.events(packageId, limit == null ? 100 : limit));
    }

    @GetMapping("/ops/change-packages/{packageId}/landing-events")
    public Response<List<Map<String, Object>>> landingEvents(@PathVariable("packageId") String packageId,
                                                              @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit,
                                                              HttpServletRequest servletRequest) {
        return events(packageId, limit, servletRequest);
    }

    @GetMapping("/ops/change-packages/{packageId}/landing-operation-runs")
    public Response<List<Map<String, Object>>> landingOperationRuns(@PathVariable("packageId") String packageId,
                                                                     @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit,
                                                                     HttpServletRequest servletRequest) {
        authorizedDetail(packageId, servletRequest);
        return success(queries.landingOperationRuns(packageId, limit == null ? 200 : limit));
    }

    @PostMapping("/ops/change-packages/{packageId}/revise")
    public Response<Map<String, Object>> revise(@PathVariable("packageId") String packageId,
                                                 @RequestBody(required = false) Map<String, Object> request,
                                                 HttpServletRequest servletRequest) {
        Map<String, Object> detail = queries.detail(packageId);
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        permissionService.assertCanRevisePackage(text(detail.get("projectId"), ""), principal);
        return success(preparation.revise(new ChangePackageCommands.Revise(packageId, request, actor(principal))));
    }

    @PostMapping("/ops/change-packages/{packageId}/validate")
    public Response<Map<String, Object>> validate(@PathVariable("packageId") String packageId,
                                                   @RequestBody(required = false) Map<String, Object> request,
                                                   HttpServletRequest servletRequest) {
        Map<String, Object> detail = queries.detail(packageId);
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        permissionService.assertCanRevisePackage(text(detail.get("projectId"), ""), principal);
        int version = intValue(request == null ? null : request.get("version"), intValue(detail.get("version"), 1));
        return success(validation.validate(new ChangePackageCommands.Validate(packageId, version, actor(principal))));
    }

    @PostMapping("/ops/change-packages/{packageId}/submit-review")
    public Response<Map<String, Object>> submitReview(@PathVariable("packageId") String packageId,
                                                       @RequestBody(required = false) Map<String, Object> request,
                                                       HttpServletRequest servletRequest) {
        Map<String, Object> detail = queries.detail(packageId);
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        permissionService.assertCanRevisePackage(text(detail.get("projectId"), ""), principal);
        return success(review.submitReview(new ChangePackageCommands.SubmitReview(packageId, request, actor(principal))));
    }

    @PostMapping("/ops/change-packages/{packageId}/approve")
    public Response<Map<String, Object>> approve(@PathVariable("packageId") String packageId,
                                                  @RequestBody(required = false) Map<String, Object> request,
                                                  HttpServletRequest servletRequest) {
        Map<String, Object> detail = queries.detail(packageId);
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        permissionService.assertCanApprovePackage(text(detail.get("projectId"), ""), principal);
        int version = intValue(request == null ? null : request.get("version"), intValue(detail.get("version"), 1));
        String packageHash = text(request == null ? null : request.get("packageHash"), "");
        return success(review.approve(new ChangePackageCommands.Approve(packageId, version, packageHash,
                actor(principal), approvalContext(request, principal))));
    }

    @GetMapping("/ops/change-packages/{packageId}/approval-channels")
    public Response<List<Map<String, Object>>> approvalChannels(@PathVariable("packageId") String packageId,
                                                                 HttpServletRequest servletRequest) {
        Map<String, Object> detail = queries.detail(packageId);
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        String projectId = text(detail.get("projectId"), "");
        permissionService.assertCanApprovePackage(projectId, principal);
        return success(approvalCards.available(projectId));
    }

    @PostMapping("/ops/change-packages/{packageId}/approval-card")
    public Response<Map<String, Object>> sendApprovalCard(@PathVariable("packageId") String packageId,
                                                           @RequestBody Map<String, Object> request,
                                                           HttpServletRequest servletRequest) {
        Map<String, Object> detail = queries.detail(packageId);
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        String projectId = text(detail.get("projectId"), "");
        permissionService.assertCanApprovePackage(projectId, principal);
        return success(approvalCards.send(
                projectId,
                required(request == null ? null : request.get("channelId"), "Channel 不能为空"),
                required(request == null ? null : request.get("target"), "审批卡目标不能为空"),
                packageId,
                actor(principal)));
    }

    @PostMapping("/ops/change-packages/{packageId}/reject")
    public Response<Map<String, Object>> reject(@PathVariable("packageId") String packageId,
                                                 @RequestBody(required = false) Map<String, Object> request,
                                                 HttpServletRequest servletRequest) {
        Map<String, Object> detail = queries.detail(packageId);
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        permissionService.assertCanRejectPackage(text(detail.get("projectId"), ""), principal);
        return success(review.reject(new ChangePackageCommands.Reject(packageId, request, actor(principal))));
    }

    @GetMapping("/ops/change-packages/{packageId}/landing-plan")
    public Response<Map<String, Object>> landingPlan(@PathVariable("packageId") String packageId,
                                                      HttpServletRequest servletRequest) {
        authorizedDetail(packageId, servletRequest);
        return success(queries.landingPlan(packageId));
    }

    @PostMapping("/ops/change-packages/{packageId}/land")
    public Response<Map<String, Object>> land(@PathVariable("packageId") String packageId,
                                               @RequestBody(required = false) Map<String, Object> request,
                                               HttpServletRequest servletRequest) {
        Map<String, Object> detail = queries.detail(packageId);
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        permissionService.assertCanLandPackage(text(detail.get("projectId"), ""), principal);
        return success(landing.land(new ChangePackageCommands.Land(packageId, request, actor(principal))));
    }

    @PostMapping("/ops/change-packages/{packageId}/cleanup")
    public Response<Map<String, Object>> cleanup(@PathVariable("packageId") String packageId,
                                                  @RequestBody(required = false) Map<String, Object> request,
                                                  HttpServletRequest servletRequest) {
        Map<String, Object> detail = queries.detail(packageId);
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        permissionService.assertCanLandPackage(text(detail.get("projectId"), ""), principal);
        return success(landing.cleanup(new ChangePackageCommands.Cleanup(packageId, request, actor(principal))));
    }

    @PostMapping("/ops/change-packages/{packageId}/verify-landing")
    public Response<Map<String, Object>> verifyLanding(@PathVariable("packageId") String packageId,
                                                       HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        Map<String, Object> detail = authorizedDetail(packageId, servletRequest);
        permissionService.assertCanLandPackage(text(detail.get("projectId"), ""), principal);
        return success(landing.verifyLanding(packageId, actor(principal)));
    }

    private Map<String, Object> authorizedDetail(String packageId, HttpServletRequest servletRequest) {
        Map<String, Object> detail = queries.detail(packageId);
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        permissionService.assertCanViewPackage(text(detail.get("projectId"), ""), principal);
        return withCapabilities(detail, principal);
    }

    private Map<String, Object> withCapabilities(Map<String, Object> source,
                                                 AdminAuthService.AuthPrincipal principal) {
        Map<String, Object> item = new LinkedHashMap<>(source == null ? Map.of() : source);
        Map<String, Boolean> capabilities = permissionService.capabilities(text(item.get("projectId"), ""), principal);
        item.put("capabilities", capabilities);
        capabilities.forEach(item::put);
        return item;
    }

    private AdminAuthService.AuthPrincipal principal(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal authPrincipal) return authPrincipal;
        throw new SecurityException("未获取到已认证用户");
    }

    private String actor(AdminAuthService.AuthPrincipal principal) {
        return StringUtils.hasText(principal.userId()) ? principal.userId() : principal.username();
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

    private void putIfText(Map<String, Object> map, String key, String value) {
        if (StringUtils.hasText(value)) map.put(key, value.trim());
    }

    private String text(Object value, String fallback) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? fallback : String.valueOf(value).trim();
    }

    private String required(Object value, String message) {
        String normalized = text(value, "");
        if (normalized.isBlank()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
