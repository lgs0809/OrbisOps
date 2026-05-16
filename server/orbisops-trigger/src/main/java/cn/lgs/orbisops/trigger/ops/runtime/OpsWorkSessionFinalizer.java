package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpsWorkSessionFinalizer {

    public OpsAgentChatResponse success(Context context, String output, Hooks hooks) {
        hooks.record(OpsRuntimeEvent.builder()
                .eventType("FINAL_OUTPUT")
                .status("SUCCEEDED")
                .content(output)
                .summary("Agent 输出完成。")
                .build());
        hooks.record(OpsRuntimeEvent.of("DONE", "SUCCEEDED", "Agent 任务执行完成。"));
        if (!context.analysisRequest()) {
            hooks.appendAssistant(output, Map.of(
                    "agentId", context.definition().getAgentId(),
                    "engine", context.engine()));
        }
        hooks.telemetrySucceeded(elapsedMs(context.startedNanos()));
        hooks.finishTaskContext("SUCCEEDED", output);
        hooks.recordSkillUsage("SUCCEEDED");
        OpsAgentChatResponse response = response(context, "SUCCEEDED", output);
        hooks.finishDurable("SUCCEEDED", null, response);
        hooks.markFinished();
        return response;
    }

    public OpsAgentChatResponse waitingApproval(Context context,
                                                String nodeId,
                                                String approvalId,
                                                Hooks hooks) {
        String safeNodeId = value(nodeId);
        String safeApprovalId = value(approvalId);
        String message = "Workflow is waiting for human approval at node " + safeNodeId + ".";
        // The typed workflow persisted its waiting event before releasing the lease.
        // Returning its response must not heartbeat or append a second durable event.
        if (!context.analysisRequest()) {
            hooks.appendAssistant(message, Map.of(
                    "agentId", context.definition().getAgentId(),
                    "engine", context.engine(),
                    "status", "WAITING_APPROVAL",
                    "runId", value(context.request().getRunId()),
                    "approvalId", safeApprovalId,
                    "nodeId", safeNodeId));
        }
        return response(context, "WAITING_APPROVAL", message);
    }

    public OpsAgentChatResponse canceled(Context context, String reason, Hooks hooks) {
        String message = value(reason);
        hooks.record(OpsRuntimeEvent.builder()
                .eventType("RUN_CANCELED")
                .status("CANCELED")
                .summary(message)
                .payload(Map.of("runId", value(context.request().getRunId()),
                        "elapsedMs", elapsedMs(context.startedNanos())))
                .build());
        hooks.record(OpsRuntimeEvent.of("DONE", "CANCELED", "Agent 任务已取消。"));
        hooks.finishTaskContext("CANCELED", message);
        if (!context.analysisRequest()) {
            hooks.appendAssistant("Agent run canceled: " + message, Map.of(
                    "agentId", context.definition().getAgentId(),
                    "engine", context.engine(),
                    "status", "CANCELED",
                    "runId", value(context.request().getRunId())));
        }
        hooks.recordSkillUsage("CANCELED");
        OpsAgentChatResponse response = response(context, "CANCELED", message);
        hooks.finishDurable("CANCELED", message, response);
        hooks.markFinished();
        return response;
    }

    public void failed(Context context, RuntimeException error, Hooks hooks) {
        FailureView failure = failureView(error);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", value(context.request().getRunId()));
        payload.put("elapsedMs", elapsedMs(context.startedNanos()));
        payload.put("reasonCode", failure.reasonCode());
        hooks.record(OpsRuntimeEvent.builder()
                .eventType("RUN_FAILED")
                .status("FAILED")
                .summary(failure.userMessage())
                .payload(payload)
                .build());
        hooks.telemetryFailed(elapsedMs(context.startedNanos()));
        hooks.finishTaskContext("FAILED", failure.reasonCode());
        if (!context.analysisRequest()) {
            hooks.appendAssistant(failure.userMessage(), Map.of(
                    "agentId", context.definition().getAgentId(),
                    "engine", context.engine(),
                    "status", "FAILED",
                    "reasonCode", failure.reasonCode()));
        }
        hooks.recordSkillUsage("FAILED");
        hooks.finishDurable("FAILED", failure.reasonCode(), null);
        hooks.markFinished();
    }

    private FailureView failureView(RuntimeException error) {
        Throwable cause = error;
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        while (cause != null && seen.add(cause)) {
            if (cause instanceof cn.lgs.orbisops.application.skill.SkillRuntimeAccessRevokedException) {
                return new FailureView("SKILL_RUNTIME_ACCESS_REVOKED",
                        "本轮使用的 Skill 已停用或项目授权已撤回，后续执行已停止。请检查项目 Skill 权限后发起新任务。");
            }
            cause = cause.getCause();
        }
        var mcpFailure = OpsMcpFailureClassifier.findTyped(error).orElse(null);
        if (mcpFailure != null) {
            String message = switch (mcpFailure.kind()) {
                case PROTOCOL_ERROR -> "工具协议响应异常";
                case TOOL_ERROR -> "工具报告业务错误";
                case CONTRACT_INVALID -> "工具回包不符合已审核契约";
                case TRANSPORT_ERROR -> "工具连接异常或回执等待超时";
                case AUTHORITY_DENIED -> "工具授权、任务期限或调用预算校验未通过";
            };
            return new FailureView("MCP_" + mcpFailure.kind().name(),
                    message + "。本轮未完成，请在运行详情查看工具记录和执行回执。");
        }
        OpsModelProviderFailureClassifier.Failure providerFailure =
                OpsModelProviderFailureClassifier.classify(error).orElse(null);
        if (providerFailure != null) {
            return new FailureView(
                    providerFailure.code(),
                    providerFailure.userMessage() + " 本轮未完成，请在运行详情核对已有工具回执与实际资源状态。"
            );
        }
        return new FailureView(
                "AGENT_RUN_FAILED",
                "本轮执行未完成。已保留运行过程，请查看任务详情并检查模型或项目连接状态后重试。"
        );
    }

    private OpsAgentChatResponse response(Context context, String status, String content) {
        Map<String, Object> metadata = "SUCCEEDED".equals(status)
                ? Map.of("eventCount", context.events().size(),
                "agentVersion", context.definition().getVersion(),
                "runId", value(context.request().getRunId()))
                : Map.of("eventCount", context.events().size(),
                "agentVersion", context.definition().getVersion(),
                "runId", value(context.request().getRunId()),
                "status", status);
        return OpsAgentChatResponse.builder()
                .sessionId(context.request().getSessionId())
                .userId(context.request().getUserId())
                .agentId(context.definition().getAgentId())
                .agentVersion(context.definition().getVersion())
                .mode(context.mode())
                .engine(context.engine())
                .content(content)
                .events(context.events())
                .metadata(metadata)
                .build();
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private record FailureView(String reasonCode, String userMessage) {
    }

    public record Context(OpsAgentChatRequest request,
                          OpsAgentDefinition definition,
                          String mode,
                          String engine,
                          List<OpsRuntimeEvent> events,
                          long startedNanos,
                          boolean analysisRequest) {
        public Context {
            if (request == null) throw new IllegalArgumentException("WORK_SESSION_REQUEST_REQUIRED");
            if (definition == null) throw new IllegalArgumentException("WORK_SESSION_DEFINITION_REQUIRED");
            mode = valueOf(mode);
            engine = valueOf(engine);
            events = events == null ? List.of() : events;
        }

        private static String valueOf(String value) {
            return value == null ? "" : value;
        }
    }

    public interface Hooks {
        void record(OpsRuntimeEvent event);

        void appendAssistant(String content, Map<String, Object> metadata);

        void telemetrySucceeded(long durationMs);

        void telemetryFailed(long durationMs);

        void finishTaskContext(String status, String summary);

        void recordSkillUsage(String status);

        void finishDurable(String status, String error, OpsAgentChatResponse response);

        void markFinished();
    }
}
