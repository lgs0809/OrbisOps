package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.analysis.AnalysisTaskFeedbackApplicationService;
import cn.lgs.orbisops.application.analysis.AnalysisTaskQueryApplicationService;
import cn.lgs.orbisops.trigger.application.analysis.OpsAnalysisTaskMapper;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ops/analysis-tasks")
public class OpsAnalysisTaskAdminController {

    private final AnalysisTaskQueryApplicationService queries;
    private final AnalysisTaskFeedbackApplicationService feedback;
    private final OpsAnalysisTaskMapper mapper;

    public OpsAnalysisTaskAdminController(
            AnalysisTaskQueryApplicationService queries,
            AnalysisTaskFeedbackApplicationService feedback,
            OpsAnalysisTaskMapper mapper) {
        this.queries = queries;
        this.feedback = feedback;
        this.mapper = mapper;
    }

    @GetMapping
    public Response<List<Map<String, Object>>> list(
            @RequestParam("projectId") String projectId,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "100") Integer limit) {
        return success(queries.list(projectId, source, status, limit == null ? 100 : limit)
                .stream()
                .map(mapper::taskView)
                .toList());
    }

    @GetMapping("/{runId}")
    public Response<Map<String, Object>> detail(
            @PathVariable("runId") String runId,
            @RequestParam("projectId") String projectId) {
        return success(mapper.detailView(queries.detail(projectId, runId)));
    }

    @PostMapping("/{runId}/feedback")
    public Response<Map<String, Object>> feedback(
            @PathVariable("runId") String runId,
            @RequestParam("projectId") String projectId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return success(mapper.feedbackView(feedback.record(
                projectId,
                runId,
                text(safe.get("feedbackType")),
                text(safe.get("comment")),
                actor(servletRequest))));
    }

    private String actor(HttpServletRequest request) {
        Object value = request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) return principal.username();
        throw new SecurityException("未获取到已认证操作者");
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
