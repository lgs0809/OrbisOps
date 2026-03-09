package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.agent.OpsActionStatus;
import cn.lgs.orbisops.application.agent.OpsFailureCategory;
import cn.lgs.orbisops.application.agent.OpsFailureDescriptor;
import cn.lgs.orbisops.application.agent.OpsMainAgentActionType;
import cn.lgs.orbisops.application.agent.OpsMainAgentCommand;
import cn.lgs.orbisops.application.agent.OpsMainAgentCoordinator;
import cn.lgs.orbisops.application.agent.OpsMainAgentOutcome;
import cn.lgs.orbisops.application.agent.OpsSideEffectState;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Trigger anti-corruption facade for Main Agent command and outcome protocol mapping. */
public final class OpsChatMainAgentExecutionFacade {

    private final OpsMainAgentCoordinator mainAgentCoordinator;

    public OpsChatMainAgentExecutionFacade(OpsMainAgentCoordinator mainAgentCoordinator) {
        if (mainAgentCoordinator == null) {
            throw new IllegalArgumentException("CHAT_MAIN_AGENT_EXECUTION_DEPENDENCIES_REQUIRED");
        }
        this.mainAgentCoordinator = mainAgentCoordinator;
    }

    public OpsAgentChatResponse execute(
            OpsAgentChatRequest request,
            Consumer<OpsRuntimeEvent> eventSink,
            boolean synchronous) {
        OpsMainAgentActionType actionType = OpsMainAgentActionType.AGENT;
        OpsMainAgentCommand command = new OpsMainAgentCommand(
                actionType,
                value(request == null ? null : request.getRunId(), ""),
                value(request == null ? null : request.getSessionId(), ""),
                value(request == null ? null : request.getProjectId(), ""),
                value(request == null ? null : request.getUserId(), ""),
                value(request == null ? null : request.getQuery(), ""),
                new OpsChatActionPayload(request, eventSink, synchronous),
                Map.of("routing", "MODEL_TOOL_SELECTION"));
        OpsMainAgentOutcome outcome = mainAgentCoordinator.execute(command);
        if (outcome.result() instanceof OpsAgentChatResponse response) {
            attachMetadata(response, actionType, outcome);
            return response;
        }
        if (outcome.isSuccessful()) {
            outcome = invalidSuccessfulResult(request);
        }
        return failureResponse(request, actionType, outcome, eventSink);
    }

    private OpsMainAgentOutcome invalidSuccessfulResult(OpsAgentChatRequest request) {
        return OpsMainAgentOutcome.failed(
                OpsActionStatus.FAILED,
                new OpsFailureDescriptor(
                        "MAIN_AGENT_HANDLER_RESULT_INVALID",
                        "RESPONSE_MAPPING",
                        OpsFailureCategory.UNKNOWN,
                        "任务处理器返回了无法识别的结果，系统没有将其标记为成功。",
                        false,
                        OpsSideEffectState.UNKNOWN,
                        List.of(),
                        List.of("VIEW_TASK_DETAIL"),
                        Map.of("runId", value(request == null ? null : request.getRunId(), ""))));
    }

    private void attachMetadata(
            OpsAgentChatResponse response,
            OpsMainAgentActionType actionType,
            OpsMainAgentOutcome outcome) {
        Map<String, Object> metadata = response.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(response.getMetadata());
        metadata.put("mainAgentAction", actionType.name());
        metadata.put("mainAgentStatus", outcome.status().name());
        if (outcome.failure() != null) {
            metadata.put("reasonCode", outcome.failure().reasonCode());
            metadata.put("sideEffectState", outcome.failure().sideEffectState().name());
            metadata.put("recoveryActions", outcome.failure().allowedRecoveryActions());
        }
        metadata.putAll(outcome.metadata());
        response.setMetadata(metadata);
    }

    private OpsAgentChatResponse failureResponse(
            OpsAgentChatRequest request,
            OpsMainAgentActionType actionType,
            OpsMainAgentOutcome outcome,
            Consumer<OpsRuntimeEvent> eventSink) {
        OpsFailureDescriptor failure = outcome.failure() == null
                ? defaultFailure()
                : outcome.failure();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", value(request == null ? null : request.getRunId(), ""));
        payload.put("reasonCode", failure.reasonCode());
        payload.put("actionType", actionType.name());
        payload.put("sideEffectState", failure.sideEffectState().name());
        payload.put("recoveryActions", failure.allowedRecoveryActions());
        OpsRuntimeEvent failed = OpsRuntimeEvent.builder()
                .eventType("MAIN_AGENT_ACTION_FAILED")
                .status(outcome.status().name())
                .summary(failure.safeUserMessage())
                .payload(payload)
                .build();
        if (eventSink != null) eventSink.accept(failed);
        return OpsAgentChatResponse.builder()
                .sessionId(value(request == null ? null : request.getSessionId(), ""))
                .userId(value(request == null ? null : request.getUserId(), ""))
                .agentId(value(request == null ? null : request.getAgentDefinitionId(), ""))
                .mode("MAIN_AGENT")
                .engine("MAIN_AGENT_COORDINATOR")
                .content(failure.safeUserMessage())
                .events(List.of(failed))
                .metadata(Map.of(
                        "runId", value(request == null ? null : request.getRunId(), ""),
                        "status", outcome.status().name(),
                        "reasonCode", failure.reasonCode(),
                        "mainAgentAction", actionType.name(),
                        "sideEffectState", failure.sideEffectState().name(),
                        "recoveryActions", failure.allowedRecoveryActions(),
                        "failed", true))
                .build();
    }

    private OpsFailureDescriptor defaultFailure() {
        return new OpsFailureDescriptor(
                "MAIN_AGENT_ACTION_FAILED",
                "MAIN_AGENT",
                OpsFailureCategory.UNKNOWN,
                "任务未完成，系统已记录失败原因。",
                false,
                OpsSideEffectState.UNKNOWN,
                List.of(),
                List.of("VIEW_TASK_DETAIL"),
                Map.of());
    }

    private String value(Object source, String fallback) {
        String text = source == null ? "" : String.valueOf(source).trim();
        return text.isBlank() ? fallback : text;
    }
}
