package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.ops.OpsChatApplicationService;
import cn.lgs.orbisops.trigger.application.security.OpsTrustedRequestMetadata;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.OpsSseFailurePayload;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.http.sse.OpsSseExecutionTemplate;
import cn.lgs.orbisops.trigger.http.sse.OpsSseStreamSession;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 运维 Agent 对话和测试运行接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ops-agent-chat")
public class OpsAgentChatAdminController {

    private final OpsChatApplicationService opsChatApplicationService;
    private final ThreadPoolExecutor opsRunExecutor;
    private final OpsSseExecutionTemplate sseTemplate;

    @Value("${orbisops.chat.stream.timeout-ms:360000}")
    private long chatStreamTimeoutMs;

    public OpsAgentChatAdminController(OpsChatApplicationService opsChatApplicationService,
                                       @Qualifier("opsRunExecutor") ThreadPoolExecutor opsRunExecutor,
                                       OpsSseExecutionTemplate sseTemplate) {
        this.opsChatApplicationService = opsChatApplicationService;
        this.opsRunExecutor = opsRunExecutor;
        this.sseTemplate = sseTemplate;
    }

    @PostMapping("/test-run")
    public Response<OpsAgentChatResponse> testRunAgent(@RequestBody OpsAgentChatRequest request, HttpServletRequest servletRequest) {
        try {
            bindTrustedPrincipal(request, principal(servletRequest));
            return Response.<OpsAgentChatResponse>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(opsChatApplicationService.testRunAgent(request))
                    .build();
        } catch (Exception e) {
            log.warn("Agent 测试运行失败：{}", e.getMessage());
            return Response.<OpsAgentChatResponse>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(OpsSseFailurePayload.summary(e))
                    .data(null)
                    .build();
        }
    }

    @PostMapping
    public Response<OpsAgentChatResponse> chat(@RequestBody OpsAgentChatRequest request, HttpServletRequest servletRequest) {
        try {
            bindTrustedPrincipal(request, principal(servletRequest));
            return Response.<OpsAgentChatResponse>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(opsChatApplicationService.adminChat(request))
                    .build();
        } catch (Exception e) {
            log.warn("Agent 对话失败：{}", e.getMessage());
            return Response.<OpsAgentChatResponse>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(OpsSseFailurePayload.summary(e))
                    .data(null)
                    .build();
        }
    }

    @PostMapping("/stream")
    public SseEmitter chatStream(@RequestBody OpsAgentChatRequest request,
                                 HttpServletRequest servletRequest,
                                 HttpServletResponse response) {
        bindTrustedPrincipal(request, principal(servletRequest));
        OpsSseStreamSession session = sseTemplate.open(
                response,
                safeStreamTimeoutMs(),
                new OpsSseExecutionTemplate.Lifecycle(
                        () -> Map.of(
                                "eventType", "DONE",
                                "status", "TIMEOUT",
                                "runId", request == null || request.getRunId() == null ? "" : request.getRunId(),
                                "summary", "Work Session 超时，已停止继续执行。"),
                        true,
                        true,
                        null,
                        () -> log.warn("管理员 Agent 流式对话超时，timeoutMs={}", safeStreamTimeoutMs()),
                        error -> { }));
        sseTemplate.announce(session, response, Map.of(
                "eventType", "STREAM_OPEN",
                "status", "RUNNING",
                "summary", "SSE 连接已建立，开始准备 Agent 流式任务。"));
        sseTemplate.submit(
                session,
                opsRunExecutor,
                stream -> opsChatApplicationService.executeAdminStream(request, stream::sendData),
                OpsSseFailurePayload::failed);
        return session.emitter();
    }

    private AdminAuthService.AuthPrincipal principal(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) return principal;
        throw new SecurityException("未获取到已认证管理员");
    }

    private void bindTrustedPrincipal(OpsAgentChatRequest request, AdminAuthService.AuthPrincipal principal) {
        OpsTrustedRequestMetadata.bindPrincipal(request, principal);
    }

    private long safeStreamTimeoutMs() {
        return Math.max(60_000L, chatStreamTimeoutMs);
    }
}
