package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.OpsAgentRunRecordDTO;
import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.analysis.AnalysisRunApplicationService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

/** Generic Agent run HTTP adapter. */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ops-agent-runs")
public class OpsAgentRunAdminController {

    private final AnalysisRunApplicationService<OpsAgentRunRequestDTO, OpsAgentRunRecordDTO, GraphEvent> analysisRuns;
    private final OpsGraphEventSseService graphEventSseService;

    public OpsAgentRunAdminController(
            AnalysisRunApplicationService<OpsAgentRunRequestDTO, OpsAgentRunRecordDTO, GraphEvent> analysisRuns,
            OpsGraphEventSseService graphEventSseService) {
        this.analysisRuns = analysisRuns;
        this.graphEventSseService = graphEventSseService;
    }

    @PostMapping
    public Response<OpsAgentRunRecordDTO> submitRun(
            @RequestBody(required = false) OpsAgentRunRequestDTO request,
            HttpServletRequest servletRequest) {
        try {
            return success(analysisRuns.submit(request == null ? new OpsAgentRunRequestDTO() : request,
                    actor(servletRequest)));
        } catch (Exception e) {
            log.error("创建 Agent 运行任务失败", e);
            return failure("创建 Agent 运行任务失败：" + e.getMessage());
        }
    }

    @GetMapping("/{runId}")
    public Response<OpsAgentRunRecordDTO> getRun(@PathVariable("runId") String runId) {
        return success(analysisRuns.get(runId).orElse(null));
    }

    @GetMapping("/{runId}/events")
    public SseEmitter streamRunEvents(@PathVariable("runId") String runId,
                                      HttpServletResponse response) {
        analysisRuns.get(runId).orElseThrow(() -> new IllegalArgumentException("ANALYSIS_RUN_NOT_FOUND:" + runId));
        return graphEventSseService.stream(runId, response);
    }

    @GetMapping("/{runId}/events/list")
    public Response<List<GraphEvent>> listRunEvents(@PathVariable("runId") String runId) {
        return success(analysisRuns.events(runId));
    }

    @GetMapping
    public Response<List<OpsAgentRunRecordDTO>> listRuns(
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        return success(analysisRuns.list(Optional.ofNullable(limit).orElse(20)));
    }

    @PostMapping("/{runId}/cancel")
    public Response<Boolean> cancelRun(@PathVariable("runId") String runId) {
        return success(analysisRuns.cancel(runId));
    }

    private String actor(HttpServletRequest request) {
        Object value = request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) return principal.username();
        throw new IllegalStateException("未获取到已认证操作者");
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private Response<OpsAgentRunRecordDTO> failure(String message) {
        return Response.<OpsAgentRunRecordDTO>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(message)
                .data(null)
                .build();
    }
}
