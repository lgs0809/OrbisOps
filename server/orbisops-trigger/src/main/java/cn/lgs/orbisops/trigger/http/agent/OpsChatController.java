package cn.lgs.orbisops.trigger.http.agent;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.trigger.application.ops.OpsChatApplicationService;
import cn.lgs.orbisops.trigger.application.security.OpsTrustedRequestMetadata;
import cn.lgs.orbisops.trigger.application.ops.OpsAgentDefinitionApplicationService;
import cn.lgs.orbisops.trigger.application.project.OpsProjectCatalogViewMapper;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.OpsSseFailurePayload;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.http.sse.OpsSseExecutionTemplate;
import cn.lgs.orbisops.trigger.http.sse.OpsSseStreamSession;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatMessageView;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSession;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionCreateRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionUpdateRequest;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent/chat")
public class OpsChatController {

    private final OpsChatApplicationService chatApplicationService;
    private final ThreadPoolExecutor opsRunExecutor;
    private final AuthorizeProjectAccessUseCase projectAccess;
    private final OpsAgentDefinitionApplicationService agentDefinitionApplicationService;
    private final OpsSseExecutionTemplate sseTemplate;

    @Value("${orbisops.chat.stream.timeout-ms:360000}")
    private long chatStreamTimeoutMs;

    public OpsChatController(OpsChatApplicationService chatApplicationService,
                             @Qualifier("opsRunExecutor") ThreadPoolExecutor opsRunExecutor,
                             AuthorizeProjectAccessUseCase projectAccess,
                             OpsAgentDefinitionApplicationService agentDefinitionApplicationService,
                             OpsSseExecutionTemplate sseTemplate) {
        this.chatApplicationService = chatApplicationService;
        this.opsRunExecutor = opsRunExecutor;
        this.projectAccess = projectAccess;
        this.agentDefinitionApplicationService = agentDefinitionApplicationService;
        this.sseTemplate = sseTemplate;
    }

    @GetMapping("/catalog/projects")
    public Response<List<Map<String, Object>>> listProjects(HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        return success(OpsProjectCatalogViewMapper.views(projectAccess.catalog(
                principal == null ? "" : principal.username(),
                principal == null ? "" : principal.userId(),
                !userScoped(principal))));
    }

    @GetMapping("/catalog/projects/{projectId}/agents")
    public Response<List<Map<String, Object>>> listProjectAgents(@PathVariable("projectId") String projectId,
                                                                 HttpServletRequest servletRequest) {
        assertProjectAccess(projectId, principal(servletRequest));
        return success(agentDefinitionApplicationService.listAgents(projectId));
    }

    @PostMapping("/session")
    public Response<String> createSession(@RequestBody(required = false) OpsChatSessionCreateRequest request,
                                          HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        assertProjectAccess(request == null ? null : request.getProjectId(), principal);
        OpsChatSession session = chatApplicationService.createSession(request, scopedUserId(principal));
        return success(session.getSessionId());
    }

    @PutMapping("/sessions/{sessionId}")
    public Response<OpsChatSession> updateSession(@PathVariable("sessionId") String sessionId,
                                                  @RequestBody(required = false) OpsChatSessionUpdateRequest request,
                                                  HttpServletRequest servletRequest) {
        return success(chatApplicationService.updateSession(sessionId, request, scopedUserId(principal(servletRequest))));
    }

    @GetMapping("/sessions/{sessionId}/participants")
    public Response<List<OpsChatSessionService.SessionParticipant>> participants(
            @PathVariable("sessionId") String sessionId,
            HttpServletRequest servletRequest) {
        return success(chatApplicationService.sessionParticipants(sessionId, scopedUserId(principal(servletRequest))));
    }

    @PutMapping("/sessions/{sessionId}/participants")
    public Response<List<OpsChatSessionService.SessionParticipant>> replaceParticipants(
            @PathVariable("sessionId") String sessionId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return success(chatApplicationService.replaceSessionParticipants(
                sessionId, longValue(safe.get("expectedSessionVersion")), participantInputs(safe.get("participants")),
                scopedUserId(principal(servletRequest))));
    }

    @GetMapping("/sessions")
    public Response<List<OpsChatSession>> listSessions(@RequestParam(value = "userId", required = false) String userId,
                                                       @RequestParam(value = "agentId", required = false) String agentId,
                                                       @RequestParam(value = "keyword", required = false) String keyword,
                                                       @RequestParam(value = "favorite", required = false) Boolean favorite,
                                                       @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit,
                                                       HttpServletRequest servletRequest) {
        return success(chatApplicationService.listSessions(userId, agentId, keyword, favorite, limit, scopedUserId(principal(servletRequest))));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Response<List<OpsChatMessageView>> listMessages(@PathVariable("sessionId") String sessionId,
                                                           @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit,
                                                           HttpServletRequest servletRequest) {
        return success(chatApplicationService.messages(sessionId, limit, scopedUserId(principal(servletRequest))));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Response<Boolean> deleteSession(@PathVariable("sessionId") String sessionId,
                                           HttpServletRequest servletRequest) {
        return success(chatApplicationService.deleteSession(sessionId, scopedUserId(principal(servletRequest))));
    }

    @GetMapping("/model-status")
    public Response<Map<String, Object>> modelStatus() {
        return success(chatApplicationService.modelStatus());
    }

    @PostMapping
    public Response<OpsAgentChatResponse> chat(@RequestBody OpsAgentChatRequest request,
                                               HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        assertProjectAccess(request == null ? null : request.getProjectId(), principal);
        bindTrustedPrincipal(request, principal);
        return success(chatApplicationService.chat(request, scopedUserId(principal)));
    }

    @PostMapping("/stream")
    public SseEmitter chatStream(@RequestBody OpsAgentChatRequest request,
                                 HttpServletRequest servletRequest,
                                 HttpServletResponse servletResponse) {
        OpsAgentChatRequest streamRequest = request == null ? new OpsAgentChatRequest() : request;
        OpsSseStreamSession stream = sseTemplate.open(
                servletResponse,
                safeStreamTimeoutMs(),
                new OpsSseExecutionTemplate.Lifecycle(
                        () -> Map.of(
                                "eventType", "DONE",
                                "status", "TIMEOUT",
                                "runId", streamRequest.getRunId(),
                                "summary", "SSE 推送已超时断开，Work Session 继续后台执行，可按 runId 恢复查看。"),
                        false,
                        false,
                        null,
                        () -> log.warn("Agent 流式对话超时，timeoutMs={}", safeStreamTimeoutMs()),
                        error -> log.debug("SSE 连接中断，Work Session 保持运行，runId={}：{}",
                                streamRequest.getRunId(),
                                error == null || error.getMessage() == null ? "unknown" : error.getMessage())));
        try {
            AdminAuthService.AuthPrincipal principal = principal(servletRequest);
            assertProjectAccess(streamRequest.getProjectId(), principal);
            bindTrustedPrincipal(streamRequest, principal);
            chatApplicationService.prepareStreamRequest(streamRequest, scopedUserId(principal));
        } catch (Exception error) {
            sseTemplate.failPreflight(stream, error, OpsSseFailurePayload.failed(error));
            return stream.emitter();
        }
        sseTemplate.announce(stream, servletResponse, Map.of(
                "eventType", "STREAM_OPEN",
                "status", "RUNNING",
                "runId", streamRequest.getRunId(),
                "summary", "SSE 连接已建立，开始准备会话。"));
        AtomicReference<OpsChatSession> chatSession = new AtomicReference<>();
        sseTemplate.submit(
                stream,
                opsRunExecutor,
                session -> {
                    OpsChatSession ready = chatApplicationService.ensureForChat(streamRequest);
                    chatSession.set(ready);
                    session.sendData(Map.of(
                            "eventType", "SESSION_READY",
                            "status", "SUCCEEDED",
                            "sessionId", ready.getSessionId(),
                            "summary", "会话已就绪。"));
                    OpsAgentChatResponse response = chatApplicationService.executeStream(streamRequest, event -> {
                        if (!session.trySendData(event)) {
                            log.debug("SSE 已断开，Work Session 继续后台执行，runId={}",
                                    streamRequest.getRunId());
                        }
                    });
                    chatApplicationService.touch(ready, response);
                },
                error -> streamFailure(error, chatSession.get()));
        return stream.emitter();
    }

    @PostMapping("/runs/{runId}/cancel")
    public Response<Boolean> cancelRun(@PathVariable("runId") String runId,
                                       @RequestParam(value = "projectId", required = false) String projectId,
                                       @RequestParam(value = "reason", required = false, defaultValue = "用户主动取消") String reason,
                                       HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        if (StringUtils.hasText(projectId)) {
            assertProjectAccess(projectId, principal);
        }
        return success(chatApplicationService.cancelRun(runId, projectId, runActor(principal), reason));
    }

    @GetMapping("/runs/{runId}")
    public Response<Map<String, Object>> run(@PathVariable("runId") String runId,
                                             @RequestParam("projectId") String projectId,
                                             HttpServletRequest servletRequest) {
        assertProjectAccess(projectId, principal(servletRequest));
        return success(chatApplicationService.run(runId, projectId));
    }

    @PostMapping("/runs/{runId}/resume")
    public Response<Map<String, Object>> resumeRun(@PathVariable("runId") String runId,
                                                   @RequestParam("projectId") String projectId,
                                                   HttpServletRequest servletRequest) {
        AdminAuthService.AuthPrincipal principal = principal(servletRequest);
        assertProjectAccess(projectId, principal);
        return success(chatApplicationService.resumeRun(runId, projectId, runActor(principal)));
    }

    @GetMapping("/runs/{runId}/events")
    public Response<List<cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent>> runEvents(
            @PathVariable("runId") String runId,
            @RequestParam("projectId") String projectId,
            @RequestParam(value = "afterSequence", defaultValue = "0") Long afterSequence,
            @RequestParam(value = "limit", defaultValue = "500") Integer limit,
            HttpServletRequest servletRequest) {
        assertProjectAccess(projectId, principal(servletRequest));
        return success(chatApplicationService.runEvents(runId,
                afterSequence == null ? 0L : afterSequence,
                limit == null ? 500 : limit,
                projectId));
    }

    private Map<String, Object> streamFailure(Throwable error, OpsChatSession session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "ERROR");
        payload.put("status", "FAILED");
        if (session != null) payload.put("sessionId", session.getSessionId());
        payload.put("summary", OpsSseFailurePayload.summary(error));
        return payload;
    }

    private long safeStreamTimeoutMs() {
        return Math.max(60_000L, chatStreamTimeoutMs);
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private AdminAuthService.AuthPrincipal principal(HttpServletRequest request) {
        Object principal = request == null ? null : request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        return principal instanceof AdminAuthService.AuthPrincipal authPrincipal ? authPrincipal : null;
    }

    private void bindTrustedPrincipal(OpsAgentChatRequest request, AdminAuthService.AuthPrincipal principal) {
        OpsTrustedRequestMetadata.bindPrincipal(request, principal);
    }

    private String scopedUserId(AdminAuthService.AuthPrincipal principal) {
        return userScoped(principal) ? principalUserId(principal) : "";
    }

    private String runActor(AdminAuthService.AuthPrincipal principal) {
        if (principal == null || principal.serviceToken()) return "";
        return principalUserId(principal);
    }

    private boolean userScoped(AdminAuthService.AuthPrincipal principal) {
        return principal != null
                && !principal.serviceToken()
                && AdminAuthService.SCOPE_USER.equals(principal.scope());
    }

    private String principalUserId(AdminAuthService.AuthPrincipal principal) {
        if (principal == null) {
            return "web-user";
        }
        return StringUtils.hasText(principal.userId()) ? principal.userId() : principal.username();
    }

    private void assertProjectAccess(String projectId, AdminAuthService.AuthPrincipal principal) {
        projectAccess.requireAccess(
                projectId,
                principal == null ? "" : principal.username(),
                principal == null ? "" : principal.userId(),
                !userScoped(principal));
    }

    private List<OpsChatSessionService.SessionParticipantInput> participantInputs(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .map(item -> new OpsChatSessionService.SessionParticipantInput(
                        String.valueOf(item.getOrDefault("userId", "")).trim(),
                        String.valueOf(item.getOrDefault("role", "OBSERVER")).trim()))
                .toList();
    }

    private long longValue(Object value) {
        try { return Long.parseLong(String.valueOf(value)); }
        catch (Exception e) { throw new IllegalArgumentException("expectedSessionVersion 必须是有效数字"); }
    }

}
